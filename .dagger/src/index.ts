/**
 * Pinry Reborn's pipeline.
 *
 * One gate, called the same way on a workstation and on a runner
 * (docs/adr/0024-three-projects-share-one-repository.md, decision 5).
 */
import {
  dag,
  CacheSharingMode,
  Container,
  Directory,
  File,
  Platform,
  ReturnType,
  argument,
  func,
  object,
} from "@dagger.io/dagger"

/**
 * What the pipeline never needs from the working tree. `.git` is deliberately kept: the
 * prose rule reads the index to know which files are tracked.
 */
const IGNORE = [
  "**/build",
  "**/.gradle",
  "**/.kotlin",
  ".dagger/sdk",
  "**/node_modules",
  "**/dist",
  "**/coverage",
  "**/src/paraglide",
]

/**
 * The two long dashes the prose rule refuses, built from code points so that this file is
 * not its own offender.
 */
const LONG_DASHES = [String.fromCodePoint(0x2014), String.fromCodePoint(0x2013)]

/** Dated documents are frozen once delivered, so the dashes they carry stay where they are. */
const FROZEN = [":!docs/specs", ":!docs/plans", ":!docs/adr", ":!docs/handoffs"]

/**
 * Gradle otherwise sizes its worker pool from the container's core count, which is the engine
 * host's and not the runner's. Pinned to a runner's four so the build has one shape everywhere;
 * lifting it on a twelve-core workstation measured 2m 49s against 2m 47s, so the pin costs nothing.
 */
const MAX_WORKERS = "--max-workers=4"

/** The build that emits `contract/openapi.json` and the fast jar the image ships. It always runs
 * here: the container starts with no build output, so nothing is ever up to date. */
const QUARKUS_BUILD = ":api-application:quarkusBuild"

/** The fast-jar layout, under `api/`. It is the whole of the image's build context. */
const FAST_JAR = "api-application/build/quarkus-app"

/** The published contract, at the path both halves of the guard read it from. */
const CONTRACT = "contract/openapi.json"

/** One document per contract major still served. Empty during the alpha, where breaking is the policy. */
const FROZEN_MAJORS = "contract/frozen"

/** The breaking-change guard. Pinned: `latest` reports a commit hash, which no reader can compare. */
const OASDIFF = "tufin/oasdiff:v1.31.0"

/**
 * What oasdiff calls a break the declared version does not admit. It rates all three `info`, one level
 * below a warning, so the guard reads the identifiers rather than the exit code.
 */
const VERSION_RULES = ["api-version-not-bumped", "api-major-version-not-bumped", "api-version-decreased"]

/**
 * The level at which a break is one. Seventeen of oasdiff's 681 changelog rules are rated a warning,
 * `request-parameter-removed` among them, so `--fail-on ERR` lets a removed query parameter through.
 */
const FAIL_ON = "WARN"

/**
 * Where the contract's previous state is read from. The remote ref and not a local `main`, which a
 * workstation may have left behind the branch it is comparing against.
 */
const MAIN_REF = "origin/main"

/** Where the previous contract lands, outside `/src` so it is not itself a candidate for comparison. */
const PREVIOUS_CONTRACT = "/previous-contract.json"

/** Node's current long term support line, which is what every version in `clients/` is pinned for. */
const NODE = "node:24-slim"

/** The pnpm `clients/package.json` names under `packageManager`. The two move together. */
const PNPM = "pnpm@12.3.4"

/** pnpm's own store for root, mounted so an install is not a fresh download every run. */
const PNPM_STORE = "/root/.local/share/pnpm/store"

/** The port the runtime image serves on. */
const HTTP_PORT = 8080

/** How long the smoke test gives the container to answer, in seconds. */
const SMOKE_SECONDS = 60

/**
 * The smoke test's body. `\${i}` is the shell's variable and not this file's: escaping it is what
 * keeps the message honest about which second answered.
 */
const POLL = `
for i in $(seq 1 ${SMOKE_SECONDS}); do
  if body=$(curl -fsS http://api:${HTTP_PORT}/q/health); then
    echo "healthy after \${i}s: $body"
    exit 0
  fi
  sleep 1
done
echo "no answer on /q/health within ${SMOKE_SECONDS}s" >&2
exit 1
`

/**
 * The contract as `main` has it. The document goes to a file and not to standard output, so the one
 * thing on the error stream is the sentence a reader needs when the ref does not resolve.
 */
const SHOW_PREVIOUS_CONTRACT = `
if git cat-file -e "${MAIN_REF}:${CONTRACT}" 2>/dev/null; then
  git show "${MAIN_REF}:${CONTRACT}" > ${PREVIOUS_CONTRACT}
  exit 0
fi
echo "No ${CONTRACT} on ${MAIN_REF}. The guard compares against main, so a shallow clone needs fetch-depth: 0." >&2
exit 1
`

@object()
export class PinryReborn {
  /**
   * The whole gate: the Gradle gate, the repository's prose rules and the contract's synchronisation.
   */
  @func()
  async gate(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Promise<string> {
    return this.gateReport(source, this.gateBuild(source))
  }

  /**
   * Everything a pull request checks, in one call: the gate, then the image built and smoked from
   * the fast jar the gate's own container produced. Two calls on two runners paid for that jar
   * twice (docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md, decision 1).
   */
  @func()
  async ci(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Promise<string> {
    const built = this.gateBuild(source)
    const gate = await this.gateReport(source, built)
    // Sequential: a gate that fails has to stop the run before any image is built.
    const context = this.imageContext(source, built.directory(`/src/api/${FAST_JAR}`))
    const image = await this.imageReport(context, [])
    const smoke = await this.smokeReport(context)
    return [gate, image, smoke].join("\n")
  }

  /**
   * The contract refuses an undeclared breaking change
   * (docs/adr/0024-three-projects-share-one-repository.md, decision 7). Two checks: the committed
   * document breaks no major still served, and its `info.version` admits what it changed against
   * `main`. The gate's synchronisation check is what makes reading the committed document sound.
   */
  @func()
  async contractGuard(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Promise<string> {
    const [stillServed, declared] = await Promise.all([
      this.breaksNoStillServedMajor(source),
      this.versionAdmitsTheDiff(source),
    ])
    return [stillServed, declared].join("\n")
  }

  /**
   * The Gradle gate alone, in a container pinning the JDK 25 toolchain and libvips.
   */
  @func()
  apiGate(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Container {
    return this.gradleRun(source, "gate")
  }

  /**
   * The JavaScript gate alone (`clients/AGENTS.md`), in a container pinning Node and pnpm.
   * The catalogues compile second and not fourth, because what Paraglide emits is what the
   * typecheck reads.
   *
   * The bundle is built last for the reason the API's gate builds the fast jar: a typecheck is
   * not a build, and a Vite plugin wired wrong passes every step above it.
   */
  @func()
  clientsGate(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Container {
    return this.node(source)
      .withExec(["pnpm", "install", "--frozen-lockfile"])
      .withExec(["pnpm", "run", "messages"])
      .withExec(["pnpm", "run", "typecheck"])
      .withExec(["pnpm", "run", "lint"])
      .withExec(["pnpm", "run", "boundaries"])
      .withExec(["pnpm", "run", "test"])
      .withExec(["pnpm", "run", "build"])
  }

  /**
   * The generated contract directory, so a caller can compare it or export it.
   */
  @func()
  contract(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Directory {
    return this.gradleRun(source, QUARKUS_BUILD).directory("/src/contract")
  }

  /**
   * The rules whose scope is the repository rather than one ecosystem: no long dash in a
   * tracked text file, and the evidence guard's own tests.
   */
  @func()
  async prose(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Promise<string> {
    const base = this.repository(source)
    // `git grep` answers by its exit status: 0 found something, 1 found nothing, above that it failed.
    // Dagger raises on 128 itself, git's code for a fatal error, so the branch below guards the rest.
    const dashes = base.withExec(
      ["git", "grep", "-n", "-I", "-F", ...LONG_DASHES.flatMap((d) => ["-e", d]), "--", ".", ...FROZEN],
      { expect: ReturnType.Any },
    )
    const guard = base.withExec([
      "python3",
      "-m",
      "unittest",
      "discover",
      "--start-directory",
      ".claude/hooks",
    ])
    const [status, offenders, failure, guardReport] = await Promise.all([
      dashes.exitCode(),
      dashes.stdout(),
      dashes.stderr(),
      guard.stderr(),
    ])
    if (status === 0) {
      throw new Error(
        "Em dash or en dash found. Use a colon, a period, parentheses or a hyphen:\n" + offenders,
      )
    }
    if (status !== 1) {
      throw new Error(`The long dash search failed (exit ${status}):\n${failure}`)
    }
    return `no long dash in a tracked text file\n${guardReport.trim()}`
  }

  /**
   * The Quarkus fast-jar layout the `Dockerfile` copies, so a caller can build the image with
   * no JDK of its own. The gate's own build and not a build of its own: the release path calls
   * this after `ci` on the same engine, so what it exports is the bytes `ci` smoked, at no
   * second build.
   */
  @func()
  quarkusApp(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Directory {
    return this.gateBuild(source).directory(`/src/api/${FAST_JAR}`)
  }

  /**
   * The runtime image. Each line is read from inside the image that was built, not from the
   * request that asked for it, so a run that built one architecture cannot report two.
   *
   * @param platforms What to build. Empty means the engine's own, which is what a pull request
   * needs: the other is emulated, costs minutes, and `buildx` builds both on the release path
   * anyway. `--platforms=linux/amd64,linux/arm64` asks for everything the image ships on.
   */
  @func()
  async image(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
    platforms: Platform[] = [],
  ): Promise<string> {
    return this.imageReport(this.imageContext(source, this.fastJar(source)), platforms)
  }

  /**
   * The image starts and reports healthy. The test suite never reads production's
   * `application.properties`, its own sharing that name and winning by classpath order, so this
   * is the only thing in the repository that starts what ships.
   */
  @func()
  async smoke(
    @argument({ defaultPath: "/", ignore: IGNORE }) source: Directory,
  ): Promise<string> {
    return this.smokeReport(this.imageContext(source, this.fastJar(source)))
  }

  /** The gate's own Gradle invocation, and the only one continuous integration ever runs. */
  private gateBuild(source: Directory): Container {
    // One Gradle invocation for both parts. Two would serialize on the shared cache volume and
    // the second would recompile what the first had just compiled.
    return this.gradleRun(source, "gate", QUARKUS_BUILD)
  }

  /** The gate's five reports, read off a build the caller may also take artefacts from. */
  private async gateReport(source: Directory, built: Container): Promise<string> {
    // The contract is read after the build, not beside it. Asking for both at once makes two
    // requests for one container, and the second waits on a cache volume the first holds.
    const [api, clients, prose, guard] = await Promise.all([
      built.stdout(),
      this.clientsGate(source).stdout(),
      this.prose(source),
      this.contractGuard(source),
    ])
    const contract = await this.contractIsSynchronised(built.directory("/src/contract"), source)
    return [api, clients, prose, contract, guard].join("\n")
  }

  /** The fast jar a caller wanting the image alone needs, without the gate that precedes it. */
  private fastJar(source: Directory): Directory {
    return this.gradleRun(source, QUARKUS_BUILD).directory(`/src/api/${FAST_JAR}`)
  }

  /** Each line read from inside the image that was built, not from the request that asked for it. */
  private async imageReport(context: Directory, platforms: Platform[]): Promise<string> {
    const wanted = platforms.length > 0 ? platforms : [await dag.defaultPlatform()]
    const lines = await Promise.all(
      wanted.map(async (platform) => {
        const machine = await context.dockerBuild({ platform }).withExec(["uname", "-m"]).stdout()
        return `${platform}: built, ${machine.trim()} inside`
      }),
    )
    return lines.join("\n")
  }

  /** The image started and probed by itself. */
  private async smokeReport(context: Directory): Promise<string> {
    // The engine's own platform: an emulated container would measure the emulator. Named rather
    // than defaulted, so this is the variant `image` built and not a second build of it.
    const platform = await dag.defaultPlatform()
    const runtime = context.dockerBuild({ platform })
    const service = runtime.withExposedPort(HTTP_PORT).asService({ useEntrypoint: true })
    // The image carries curl for its own HEALTHCHECK, so it is its own prober.
    const probe = runtime
      .withServiceBinding("api", service)
      .withExec(["sh", "-c", POLL], { expect: ReturnType.Any })
    const [status, report, failure] = await Promise.all([
      probe.exitCode(),
      probe.stdout(),
      probe.stderr(),
    ])
    if (status !== 0) {
      throw new Error(`The image never reported healthy (exit ${status}):\n${failure}`)
    }
    return report.trim()
  }

  /**
   * The `Dockerfile` and the one directory it copies, and nothing else. A context built from
   * exactly what the image needs keys the build on the artefact instead of on the working tree.
   */
  private imageContext(source: Directory, fastJar: Directory): Directory {
    return dag
      .directory()
      .withFile("Dockerfile", source.file("api/Dockerfile"))
      .withDirectory(FAST_JAR, fastJar)
  }

  /**
   * The committed contract is what the build produces, or the gate names the command that
   * makes it so. The pre-commit hook used to rewrite it mid-commit; this refuses instead.
   *
   * The command names the augmentation task and `--rerun`, not `quarkusBuild`: a document
   * edited by hand leaves every source untouched, so an ordinary build is up to date and
   * writes nothing, and the developer would run the command and see no change.
   */
  private async contractIsSynchronised(
    produced: Directory,
    source: Directory,
  ): Promise<string> {
    const [generated, committed] = await Promise.all([
      produced.file("openapi.json").contents(),
      source.file("contract/openapi.json").contents(),
    ])
    if (generated !== committed) {
      throw new Error(
        "contract/openapi.json is not what the build produces. Regenerate and commit it:\n" +
          "  cd api && ./gradlew :api-application:quarkusAppPartsBuild --rerun",
      )
    }
    return "contract/openapi.json is synchronised"
  }

  /**
   * Nothing a client of a still served major reads may disappear. The directory is empty during the
   * alpha, so this check has nothing to compare and says so rather than reporting a pass it did not earn.
   */
  private async breaksNoStillServedMajor(source: Directory): Promise<string> {
    // Git keeps the directory with a `.gitkeep` alone, so a tree that lost it has to read as no
    // major still served rather than fail the pipeline on a missing path.
    const entries = await source.directory(FROZEN_MAJORS).entries().catch((): string[] => [])
    const majors = entries.filter((entry) => entry.endsWith(".json"))
    if (majors.length === 0) {
      return `${FROZEN_MAJORS}/ holds no still served major to break`
    }
    const oasdiff = this.oasdiff(source)
    const reports = await Promise.all(
      majors.map(async (major) => {
        // Base then revision: swapped, a removal reads as an addition and every break passes.
        const run = oasdiff.withExec(
          ["breaking", `${FROZEN_MAJORS}/${major}`, CONTRACT, "--fail-on", FAIL_ON, "--format", "singleline"],
          { useEntrypoint: true, expect: ReturnType.Any },
        )
        const [status, changes] = await Promise.all([run.exitCode(), run.stdout()])
        return { major, status, changes }
      }),
    )
    const broken = reports.filter((report) => report.status !== 0)
    if (broken.length > 0) {
      throw new Error(
        `${CONTRACT} breaks a major that is still served. Restore what it removed, or retire that major:\n` +
          broken.map((report) => `  against ${FROZEN_MAJORS}/${report.major}:\n${report.changes}`).join("\n"),
      )
    }
    return `${CONTRACT} breaks none of the still served majors: ${majors.join(", ")}`
  }

  /**
   * A break is allowed; a break the version hides is not. Two readings of the same diff: oasdiff's own
   * version rules, which name the bump they wanted, and a `breaking` run, whose warning-level findings
   * those rules never see and which therefore needs the majors compared here.
   */
  private async versionAdmitsTheDiff(source: Directory): Promise<string> {
    const previous = await this.contractOnMain(source)
    const oasdiff = this.oasdiff(source).withFile(PREVIOUS_CONTRACT, previous)
    const changelogRun = oasdiff.withExec(
      ["changelog", PREVIOUS_CONTRACT, CONTRACT, "--format", "json"],
      { useEntrypoint: true },
    )
    const breakingRun = oasdiff.withExec(
      ["breaking", PREVIOUS_CONTRACT, CONTRACT, "--fail-on", FAIL_ON, "--format", "singleline"],
      { useEntrypoint: true, expect: ReturnType.Any },
    )
    const [changelog, breakingStatus, breaks, previousVersion, declaredVersion] = await Promise.all([
      changelogRun.stdout(),
      breakingRun.exitCode(),
      breakingRun.stdout(),
      this.declaredVersion(previous),
      this.declaredVersion(source.file(CONTRACT)),
    ])
    const changes: { id: string; text: string }[] = JSON.parse(changelog || "[]")
    const hidden = changes.filter((change) => VERSION_RULES.includes(change.id))
    if (hidden.length > 0) {
      throw new Error(
        `${CONTRACT} breaks and its info.version does not admit it. Raise ` +
          "quarkus.smallrye-openapi.info-version, then regenerate the contract:\n" +
          hidden.map((change) => `  ${change.text} [${change.id}]`).join("\n"),
      )
    }
    // A version nothing can parse reads as no raise, so a document the guard cannot read fails.
    const raised = this.major(declaredVersion) > this.major(previousVersion)
    if (breakingStatus !== 0 && !raised) {
      throw new Error(
        `${CONTRACT} breaks against main and announces ${declaredVersion}, where main announces ` +
          `${previousVersion}. Raise quarkus.smallrye-openapi.info-version by a major, then ` +
          "regenerate the contract:\n" +
          breaks,
      )
    }
    return `${CONTRACT}'s info.version admits every break in the diff against main`
  }

  /** A contract's declared `info.version`, empty when the document carries none. */
  private async declaredVersion(contract: File): Promise<string> {
    const document = JSON.parse(await contract.contents())
    return document?.info?.version ?? ""
  }

  /** The major a break has to raise. `NaN` for anything else, which compares false either way. */
  private major(version: string): number {
    return Number.parseInt(version, 10)
  }

  /** The committed contract on `main`, which is what a merge would replace. */
  private async contractOnMain(source: Directory): Promise<File> {
    const run = this.repository(source).withExec(["sh", "-c", SHOW_PREVIOUS_CONTRACT], {
      expect: ReturnType.Any,
    })
    const [status, failure] = await Promise.all([run.exitCode(), run.stderr()])
    if (status !== 0) {
      throw new Error(failure.trim())
    }
    return run.file(PREVIOUS_CONTRACT)
  }

  /** The guard's environment: one static binary, and the working tree it reads the contract from. */
  private oasdiff(source: Directory): Container {
    return dag.container().from(OASDIFF).withMountedDirectory("/src", source).withWorkdir("/src")
  }

  /** The build, asked for one set of tasks. Every Gradle call in this module goes through here. */
  private gradleRun(source: Directory, ...tasks: string[]): Container {
    return this.gradle(source).withExec(["./gradlew", ...tasks, "--no-daemon", MAX_WORKERS])
  }

  /**
   * The Gradle environment. The JDK is the toolchain the build asks for, so Gradle adopts it
   * instead of provisioning one; libvips is what vips-ffm loads, under the t64 name Ubuntu
   * gives it after the 64-bit time_t transition.
   */
  private gradle(source: Directory): Container {
    return dag
      .container()
      .from("eclipse-temurin:25-jdk")
      .withExec(["apt-get", "update"])
      .withExec(["apt-get", "install", "-y", "--no-install-recommends", "libvips42t64"])
      // One volume, locked. Gradle takes exclusive file locks inside its home, so two
      // invocations sharing it make one fail on the journal lock; locked serializes them
      // instead. One volume and not two, because two locks taken in either order deadlock.
      .withMountedCache("/root/.gradle", dag.cacheVolume("gradle-home"), {
        sharing: CacheSharingMode.Locked,
      })
      .withMountedDirectory("/src", source)
      .withWorkdir("/src/api")
  }

  /**
   * The JavaScript environment. pnpm comes from npm rather than from corepack, which Node
   * ships deprecated, and its version is the one `clients/package.json` pins, so nothing
   * self-manages mid-run.
   */
  private node(source: Directory): Container {
    return dag
      .container()
      .from(NODE)
      .withExec(["npm", "install", "--global", PNPM])
      // Locked like the Gradle home, and free here: one call never runs two clients gates,
      // so serializing costs nothing and a concurrent one cannot half write the store.
      .withMountedCache(PNPM_STORE, dag.cacheVolume("pnpm-store"), {
        sharing: CacheSharingMode.Locked,
      })
      .withMountedDirectory("/src", source)
      .withWorkdir("/src/clients")
  }

  /** The repository-wide environment: git for the tracked file list, python3 for the guard's tests. */
  private repository(source: Directory): Container {
    return dag
      .container()
      .from("debian:trixie-slim")
      .withExec(["apt-get", "update"])
      .withExec(["apt-get", "install", "-y", "--no-install-recommends", "git", "python3"])
      .withMountedDirectory("/src", source)
      .withWorkdir("/src")
  }
}
