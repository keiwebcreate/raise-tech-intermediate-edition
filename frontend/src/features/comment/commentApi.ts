// コメント・返信と、コメントへのいいねの API（docs/features/comment.md 8章・like.md 8章）
import { api } from '../../api/client.ts'
import type { LikersPage } from '../like/likeApi.ts'
import type { LikeState } from '../like/likeToggler.ts'

export type Comment = {
  id: number
  postId: number
  // 返信先のコメント。投稿への直接のコメントなら null
  parentCommentId: number | null
  // 返信が並ぶ一番上のコメント。投稿への直接のコメントなら null
  rootCommentId: number | null
  content: string
  createdAt: string
  author: { id: number; username: string; displayName: string }
  // 返信先のコメントを書いた人。直接のコメントなら null
  replyTo: { username: string } | null
  // 自分のコメントか（「削除」を出すか）
  mine: boolean
  likeCount: number
  liked: boolean
  // 直接付いた返信の数（「↩ 返信 N」）
  replyCount: number
  // 下に並ぶ返信すべての数（「返信を表示（N件）」）。返信なら 0
  threadReplyCount: number
}

export type CommentsPage = {
  // 投稿への直接のコメント（古い順に50件まで）
  comments: Comment[]
  // 続きを読むときに after に入れる値。続きがなければ null
  nextAfter: number | null
}

export async function getComments(postId: number, after?: number): Promise<CommentsPage> {
  return (await api.get<CommentsPage>(`/api/posts/${postId}/comments`, { params: { after } })).data
}

// 一番上のコメントの下に並ぶ返信すべて（返信の返信も含む。古い順）
export async function getReplies(commentId: number): Promise<Comment[]> {
  return (await api.get<{ replies: Comment[] }>(`/api/comments/${commentId}/replies`)).data.replies
}

// parentCommentId があれば、そのコメントへの返信
export async function createComment(postId: number, content: string, parentCommentId?: number): Promise<Comment> {
  return (await api.post<Comment>(`/api/posts/${postId}/comments`, { content, parentCommentId })).data
}

export async function deleteComment(commentId: number): Promise<void> {
  await api.delete(`/api/comments/${commentId}`)
}

// liked が true ならいいね（PUT）、false なら取り消し（DELETE）
export async function setCommentLike(commentId: number, liked: boolean): Promise<LikeState> {
  const url = `/api/comments/${commentId}/like`
  return (liked ? await api.put<LikeState>(url) : await api.delete<LikeState>(url)).data
}

export async function getCommentLikers(commentId: number, before?: number): Promise<LikersPage> {
  return (await api.get<LikersPage>(`/api/comments/${commentId}/likes`, { params: { before } })).data
}
