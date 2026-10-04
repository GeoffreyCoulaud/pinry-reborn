import "@testing-library/jest-dom/vitest";
import { toast } from "@heroui/react";
import { cleanup, configure } from "@testing-library/react";
import { afterAll, afterEach } from "vitest";
import { ReachedSentinelObserver } from "./ReachedSentinelObserver";
import { server } from "./server";

// Testing Library waits one second for a query by default, which the gate's loaded container
// spends on the first render and the four requests behind it: at that bound a journey asserts
// the machine's speed. Kept under Vitest's own per-test timeout so a real absence reports as one.
configure({ asyncUtilTimeout: 4_000 });

// Interception starts here rather than in `beforeAll`, and the difference is not cosmetic:
// `openapi-fetch` reads `globalThis.fetch` when the client is built, the application builds
// its client while `session.ts` is imported, and a setup file runs before that import while a
// hook runs after it. Started late, every journey reaches the real network and reads whatever
// answers on the test origin. An unhandled request fails the test rather than warning, so a
// route no journey declared cannot pass on a silent network failure.
server.listen({ onUnhandledRequest: "error" });

// jsdom implements no IntersectionObserver, and the grid's load-more sentinel is one. The stub
// reports the sentinel as reached, which is what react-aria's own infinite viewport under test
// already makes every tile: a journey therefore accumulates every page its catalogue serves.
globalThis.IntersectionObserver = ReachedSentinelObserver;

// jsdom implements no Web Animations API, and react-aria reads the animations of the element it
// carries from one tab to the next. Nothing animates under test, so there are none to report.
Element.prototype.getAnimations = () => [];

// jsdom implements no ResizeObserver, and HeroUI's toast measures its own height with one. It
// never reports: jsdom lays nothing out, so the only width it could announce is zero.
globalThis.ResizeObserver = class {
	observe() {}
	unobserve() {}
	disconnect() {}
};

// jsdom decodes no image, and the creation screen measures one to read it against the
// deployment's pixel limit. The stub reports a small picture, so what a journey exercises is the
// weight half of the refusal; the pixel half is held by `uploadRefusal`'s own tests.
globalThis.createImageBitmap = () =>
	Promise.resolve({ width: 100, height: 100, close: () => {} } as ImageBitmap);

// jsdom loads no video, so a source set fires neither `loadedmetadata` nor `error`: the stub reads a
// small frame from any source, as `createImageBitmap`'s does.
Object.defineProperties(HTMLVideoElement.prototype, {
	src: {
		configurable: true,
		get(this: HTMLVideoElement) {
			return this.getAttribute("src") ?? "";
		},
		set(this: HTMLVideoElement, url: string) {
			this.setAttribute("src", url);
			queueMicrotask(() => this.dispatchEvent(new Event("loadedmetadata")));
		},
	},
	videoWidth: { configurable: true, get: () => 100 },
	videoHeight: { configurable: true, get: () => 100 },
});

// jsdom implements no `decode`, and loads no image to decode: an original never arrives unless a test says so.
HTMLImageElement.prototype.decode = () => new Promise(() => {});

// jsdom decodes no video and answers `""` to every type: the stub plays what it is given unless a test says so.
HTMLMediaElement.prototype.canPlayType = () => "maybe";

// jsdom implements no matchMedia, and the theme switch reads one to resolve `system`.
globalThis.matchMedia = (media: string) =>
	({
		media,
		matches: false,
		onchange: null,
		addEventListener: () => {},
		removeEventListener: () => {},
		addListener: () => {},
		removeListener: () => {},
		dispatchEvent: () => false,
	}) as MediaQueryList;

// Testing Library cleans up by itself only when Vitest exposes its globals, which it does not here.
afterEach(cleanup);
// The toast queue is a module-level singleton, so a toast one journey raised outlives the render
// that showed it and would answer the next journey's query.
afterEach(() => toast.clear());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
