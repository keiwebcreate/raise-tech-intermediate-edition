// ブラウザに小さな値を覚えておく（前回のタブなど）。
// プライベートウィンドウなどでは使えないことがあるので、失敗しても止まらないようにする

export function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 覚えられなくても、画面はそのまま動く
  }
}
