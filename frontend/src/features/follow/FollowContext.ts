// フォロー・解除を、どの画面からでも呼べるようにする入れ物。中身は FollowProvider が用意する。
// 画面の中で同じ人が何か所に出ても、ボタンの状態をそろえる（ユーザーの決定、2026-11-15）
import { createContext, useContext, useEffect } from 'react'
import type { FollowEvent } from './followState.ts'

export type FollowTarget = { id: number; username: string }

type FollowContextValue = {
  // この画面で押したあとの状態（人の ID → フォローしているか）。押していない人は入っていない
  overrides: ReadonlyMap<number, boolean>
  // 送っている途中の人の ID
  pending: ReadonlySet<number>
  // ボタンを押した。current：今の表示。フォロー中なら解除の確認（M-03）を出してから送る
  toggle: (target: FollowTarget, current: boolean) => void
  // フォロー・解除が済んだ知らせを受け取る。返り値は受け取りをやめる関数
  subscribe: (listener: (event: FollowEvent) => void) => () => void
}

export const FollowContext = createContext<FollowContextValue | null>(null)

export function useFollowActions(): FollowContextValue {
  const value = useContext(FollowContext)
  if (!value) throw new Error('useFollowActions は FollowProvider の中で使ってください')
  return value
}

// その人の今のフォローの状態。この画面で押していれば押したあとの状態、押していなければサーバーから来た値
export function useFollowing(userId: number, fromServer: boolean): { following: boolean; pending: boolean } {
  const { overrides, pending } = useFollowActions()
  return { following: overrides.get(userId) ?? fromServer, pending: pending.has(userId) }
}

// フォロー・解除が済んだ知らせを、画面が表示されている間だけ受け取る
export function useFollowEvents(listener: (event: FollowEvent) => void): void {
  const { subscribe } = useFollowActions()
  useEffect(() => subscribe(listener), [subscribe, listener])
}
