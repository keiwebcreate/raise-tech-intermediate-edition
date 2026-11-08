// 投稿とタイムラインの API（docs/features/post.md 8章・timeline.md 4.1）
import { api } from '../../api/client.ts'

export type Post = {
  id: number
  content: string
  createdAt: string
  // 編集した日時。編集していなければ null
  editedAt: string | null
  author: { id: number; username: string; displayName: string }
  // 自分の投稿か（「…」メニューを出すか）
  mine: boolean
}

export type Tab = 'all' | 'following'

export type TimelinePage = {
  posts: Post[]
  // まだ続きがあるか
  hasMore: boolean
}

// タイムライン。before：この投稿より古いもの（続き）、after：この投稿より新しいもの（新しい投稿）
export async function getTimeline(tab: Tab, cursor: { before?: number; after?: number } = {}): Promise<TimelinePage> {
  return (await api.get<TimelinePage>('/api/posts', { params: { tab, ...cursor } })).data
}

// 一覧の一番上の投稿（after）より新しい、自分以外の投稿の数
export async function getNewPostCount(tab: Tab, after: number | undefined): Promise<number> {
  return (await api.get<{ count: number }>('/api/posts/new-count', { params: { tab, after } })).data.count
}

export async function getPost(id: number): Promise<Post> {
  return (await api.get<Post>(`/api/posts/${id}`)).data
}

export async function createPost(content: string): Promise<Post> {
  return (await api.post<Post>('/api/posts', { content })).data
}

export async function updatePost(id: number, content: string): Promise<Post> {
  return (await api.put<Post>(`/api/posts/${id}`, { content })).data
}

export async function deletePost(id: number): Promise<void> {
  await api.delete(`/api/posts/${id}`)
}
