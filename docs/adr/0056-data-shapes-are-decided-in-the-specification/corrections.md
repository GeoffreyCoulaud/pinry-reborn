# The operator's data-modelling corrections, before ADR 0056

Read-only sweep by an agent on 2026-10-09 at `1fc81bdc`: every handoff's Tier-2, operator's-review and departures
section, the specifications' decisions and `(Corrected: ...)` notes, the ADRs, `.reviews/`, `git log --all`, the
lead's memory, and the operator's review comments on GitHub read through `gh api` (99 inline comments, 3 review
bodies), the only place many of the quotes below survive. Abbreviations: H = `docs/handoffs/<date> - handoff - <slug>.md`,
S = `docs/specs/`, A = `docs/adr/`. Line numbers are that commit's.

## 1. Domain types

- **#364, `PersonUrls.kt:1`**: « Pourquoi le domaine fait du parsing pour la DB ? Le domaine ne doit pas se soucier des
  couches externes, une personne doit avoir une `List` d'URLs dans ce cas, pas un objet custom. » A domain value type
  held the canonical storage form; `Person.urls` became a `List<String>` and the canonical form moved to
  `PersonModelMapper.canonicalUrls` (fix-back `4e6a725f`). H 2026-10-08 the-pin-credits-its-people:195-203.
- **#337, `Media.kt:1`**: « Si on utilise des sealed classes / interfaces on pourrait représenter les états légaux plus
  précisément, avec les vidéos qui ont obligatoirement un video bitrate, et les images fixes / animations pour qui le
  concept de canaux et bitrate audio n'ont pas de sens. » A flat `Media` with independently nullable video and audio
  fields became a sealed interface; a video's sound is optional as one whole. H 2026-10-06 the-duplicates-are-compared:188,
  249-251.
- **ADR 0049**: `Image` renamed `Media` in code, routes, tables and keys wherever it names the pin's medium; tables
  dropped (« on casse des choses »). S 2026-10-02-the-pin-holds-a-video.md:70-73.

## 2. Identity and uniqueness

- **A person** is an entity per owner, identified by its folded name and its addresses together; found or created,
  never modified. S 2026-10-08-the-pin-credits-its-people.md:83-96; A 0055 decision 8.
- **Recycled rows hold their name**: the board and tag name indexes cover soft-deleted rows ("Operator decision").
  S 2026-08-14-user-data-import.md:759; A 0015 decision 2.
- **Natural keys**: tags and boards by name, a pin by its medium's SHA-256, archive ids discarded (A 0015 decision 2). A
  post-address source identity was rejected (A 0055 decision 15).
- **A remote collection** is identified by its address; matching by name alone was rejected (A 0055 decisions 11, 16).
- **#45/#46, `UserPasswordHashRepository`**: « On ne risque pas de renvoyer des faux positifs en cas d'autres erreurs
  de persistence, et les prendre pour des collisions d'unicité ? » The catch narrowed to `SQLITE_CONSTRAINT_UNIQUE`.
- **#45/#46, `1.18.sql`**: « est-on sûr qu'ebean ne peut pas générer automatiquement cette migration ? » Led to
  `@Index(definition = ...)`, now `api/AGENTS.md`.
- **#246, `PinRepository.kt:191`**: « A-t-on les index nécessaires pour faire ce traitement efficacement ? » Migration
  `1.27` made both join tables unique on their pair and indexed the other side (`749c13cc`).
- **Duplicate pairs** are stored with their ids in ascending order. S 2026-10-05-the-pin-knows-its-duplicates.md
  decision J.

## 3. Storage

- **#364, `PersonModel.kt:23`**: « Pourquoi pas utiliser un array, ou du json ? » Line-feed-joined text became a JSON
  array, and the line-feed rule went. H 2026-10-08:200-203.
- **No binary blobs in the database**: « Ce n'est jamais une bonne idée. Tout projet confondu. »
- **#81, `PinController.kt:110`**: the contract had said `nocase`; it now states the ASCII fold, never the collation.
  S 2026-09-05-p2-debt-elimination.md:535-539.
- **#242, `BoardCreator.kt:39`**: « Dans le cas d'une sélection longue, on fait N saves, le foreach dans la transaction
  est un code smell. » Bulk reads, one batch, only changed rows. S 2026-09-27-the-selection-starts-a-board.md:75-86.
- **#166, `PinUpdater.kt:51`**: « Ressemble à un footgun. Notre opération n'est pas atomique. » Find-or-create moved
  inside the write's transaction. H 2026-09-20 the-pin-is-editable-and-the-boards-arrive:100-104.
- **Derived data is not stored**: the board cover is derived; no filing-date column. S 2026-10-05-the-boards-wear-their-cover.md:38,
  45-49.
- **An unread column is deleted**, `session_tokens.when_created` kept with a named reader. S 2026-07-29-end-of-audited-base-model.md:87-94.
- **Pending pairs**: « le balayage périodique est quand même nécessaire pour éviter les éléments morts suite à une
  coupure inattendue. » Deletion at the site and the sweep. H 2026-10-05 the-pin-knows-its-duplicates:167-169.
- **No backfill** in the alpha. H 2026-07-26 application-test-db-isolation:34-35; S 2026-10-05-the-duplicates-are-compared.md:127-130.

## 4. Nullability

- **#242, `PinUpdateInputDto.kt:18`**: « Cette notation fait doublon en Kotlin, `List<UUID?>` serait le type qui accepte
  des nulls. » Jackson's strict null checks read the Kotlin type (`9d34980b`). H 2026-09-27 the-selection-starts-a-board:21-26.
- **`sourceMediaUrl` optional** (« Oui, il peut. »), then `sourceContextUrl` nullable in every layer.
  H 2026-09-11 web-application:293-295; S 2026-09-13-optional-source-page-url.md.
- **The pin's people and date are optional whatever the entry path** (A 0055 decision 7).

## 5. Contract

- **People named `{name, urls}`, never by id** (S 2026-10-08 decision C).
- **Codes**: a state is a closed enum, a reason an extensible one beside it; export gone states collapse to `GONE`.
  S 2026-09-26-the-codes-declare-their-sets.md:54-118.
- **413 on every route with a body**, structurally (H 2026-09-23 the-refusals-are-declared:71).
- **A body variant stays in the body** (S 2026-09-27 decision G).
- **#184**: « On ne teste pas l'absence d'un champ supprimé. »
- **The server writes the addresses**: `coverUrl` required and nullable (S 2026-10-05-the-boards-wear-their-cover.md
  decision C).

## 6. Archive

- **A person travels inside the pin lines** (S 2026-10-08 decision D): overturned by ADR 0056.
- **`publishedAt` restored unclamped**, and **`formatVersion` stays 2** (S 2026-10-08 decision D). The operator's words,
  « Cette question n'a pas de sens... on ne serait même pas obligé de bump le format », survive only in the lead's
  notes of 2026-10-08, outside the repository.
- **Tags stay plain names; source title, text, address and classification not stored** (A 0055 decisions 9, 10). No
  post entity (A 0054 decision 19).

## 7. Interface (outside ADR 0056)

- **#375**: « Dans la UI, le champ "creators" est confus. [...] il faudrait amener un système de champ multi-string,
  possiblement comment on fait pour les tags. » Then: « Tu as utilisé le champ multi valeur pour Publisher, qui est
  mono-valeur, c'est une erreur. » H 2026-10-08:204-217.

## 8. Cross-cutting

- **#340, `DuplicateResolver.kt:51`**: « Peut devenir faux. On doit apporter une réponse structurelle pour l'empécher. »
