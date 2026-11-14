// 投稿へのいいねの API（docs/features/like.md 8章）
import { api } from '../../api/client.ts'
import type { LikeState } from './likeToggler.ts'

// いいねした人（自己紹介・フォローの状態は、その機能のときに足す）
export type Liker = { id: number; username: string; displayName: string }

export type LikersPage = {
  users: Liker[]
  // 続きを読むときに before に入れる値。続きがなければ null
  nextBefore: number | null
}

// liked が true ならいいね（PUT）、false なら取り消し（DELETE）。どちらも何回送っても同じ結果
export async function setPostLike(postId: number, liked: boolean): Promise<LikeState> {
  const url = `/api/posts/${postId}/like`
  return (liked ? await api.put<LikeState>(url) : await api.delete<LikeState>(url)).data
}

export async function getPostLikers(postId: number, before?: number): Promise<LikersPage> {
  return (await api.get<LikersPage>(`/api/posts/${postId}/likes`, { params: { before } })).data
}
