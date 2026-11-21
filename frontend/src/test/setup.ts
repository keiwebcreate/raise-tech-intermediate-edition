// どのテストの前にも読む準備（vite.config.ts の test.setupFiles）
// - toBeInTheDocument() などの「画面の確かめ方」を足す（jest-dom）
// - テストごとに、描いた画面を片付ける（次のテストに前の画面が残らないように）
// - jsdom（ブラウザの代わり）にない機能のにせものを入れる
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { FakeIntersectionObserver } from './intersection.ts'

globalThis.IntersectionObserver = FakeIntersectionObserver as unknown as typeof IntersectionObserver

// 手元のファイルを表示するための URL（動画の表示・長さの読み取り）。jsdom にはないので、決まった文字を返す
if (!URL.createObjectURL) {
  URL.createObjectURL = () => 'blob:test'
  URL.revokeObjectURL = () => {}
}

afterEach(() => {
  cleanup()
  FakeIntersectionObserver.reset()
  vi.restoreAllMocks()
})
