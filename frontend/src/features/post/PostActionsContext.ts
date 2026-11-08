// 投稿・編集・削除を、どの画面からでも呼べるようにする入れ物。中身は PostActionsProvider が用意する
import { createContext, useContext, useEffect } from 'react'
import type { Post } from './postApi.ts'

// 投稿が変わったことの知らせ。タイムライン・投稿の詳細は、これを受けて一覧や表示を直す
export type PostEvent =
  { type: 'created'; post: Post } | { type: 'updated'; post: Post } | { type: 'deleted'; id: number }

type PostActionsValue = {
  // 投稿のモーダル（M-01）を開く
  openCreate: () => void
  // 編集のモーダル（M-01）を開く
  openEdit: (post: Post) => void
  // 削除の確認（M-03）を出す
  requestDelete: (post: Post) => void
  // 投稿した（タイムラインの上の入力欄など、モーダル以外から投稿したとき）ことを知らせる
  emit: (event: PostEvent) => void
  // 知らせを受け取る。返り値は受け取りをやめる関数
  subscribe: (listener: (event: PostEvent) => void) => () => void
}

export const PostActionsContext = createContext<PostActionsValue | null>(null)

export function usePostActions(): PostActionsValue {
  const value = useContext(PostActionsContext)
  if (!value) throw new Error('usePostActions は PostActionsProvider の中で使ってください')
  return value
}

// 投稿が変わった知らせを、画面が表示されている間だけ受け取る
export function usePostEvents(listener: (event: PostEvent) => void): void {
  const { subscribe } = usePostActions()
  useEffect(() => subscribe(listener), [subscribe, listener])
}
