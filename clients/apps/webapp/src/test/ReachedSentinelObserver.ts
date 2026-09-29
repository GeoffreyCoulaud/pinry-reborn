/** An `IntersectionObserver` that reports every target as reached the moment it is observed. */
export class ReachedSentinelObserver implements IntersectionObserver {
	readonly root = null;
	readonly rootMargin = "";
	readonly scrollMargin = "";
	readonly thresholds: readonly number[] = [];
	private readonly reached: IntersectionObserverCallback;
	constructor(reached: IntersectionObserverCallback) {
		this.reached = reached;
	}
	observe(target: Element) {
		this.reached(
			[{ isIntersecting: true, target } as IntersectionObserverEntry],
			this,
		);
	}
	unobserve() {}
	disconnect() {}
	takeRecords(): IntersectionObserverEntry[] {
		return [];
	}
}
