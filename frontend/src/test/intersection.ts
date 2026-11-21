// 「一番下が見えたら続きを読む」に使う IntersectionObserver（画面に入ったかを知らせる仕組み）のにせもの。
// jsdom は画面の見え方を計算しないので、テストから intersect() を呼んで「見えた」ことにする
type Callback = (entries: IntersectionObserverEntry[], observer: IntersectionObserver) => void

export class FakeIntersectionObserver {
  private static instances: FakeIntersectionObserver[] = []

  readonly root = null
  readonly rootMargin = ''
  readonly thresholds: number[] = []
  private readonly callback: Callback
  private readonly targets = new Set<Element>()

  constructor(callback: Callback) {
    this.callback = callback
    FakeIntersectionObserver.instances.push(this)
  }

  observe(target: Element): void {
    this.targets.add(target)
  }

  unobserve(target: Element): void {
    this.targets.delete(target)
  }

  disconnect(): void {
    this.targets.clear()
  }

  takeRecords(): IntersectionObserverEntry[] {
    return []
  }

  // 見張っている要素がすべて画面に入ったことにする
  static intersect(): void {
    for (const observer of FakeIntersectionObserver.instances) {
      const entries = [...observer.targets].map(
        (target) => ({ isIntersecting: true, target, intersectionRatio: 1 }) as IntersectionObserverEntry,
      )
      if (entries.length > 0) observer.callback(entries, observer as unknown as IntersectionObserver)
    }
  }

  // 何かを見張っている IntersectionObserver があるか
  static isObserving(): boolean {
    return FakeIntersectionObserver.instances.some((observer) => observer.targets.size > 0)
  }

  static reset(): void {
    FakeIntersectionObserver.instances = []
  }
}
