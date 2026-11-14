// 投稿が変わった知らせ（PostEvent）を、投稿の一覧に反映する。タイムラインとプロフィールで使う
import type { PostEvent } from './PostActionsContext.ts'
import type { Post } from './postApi.ts'

// 新しい投稿を上に足す。同じ投稿を2回出さない
export function mergeNewer(newer: Post[], current: Post[]): Post[] {
  const ids = new Set(newer.map((p) => p.id))
  return [...newer, ...current.filter((p) => !ids.has(p.id))]
}

// 続き（古い投稿）を下に足す。同じ投稿を2回出さない
export function appendOlder(current: Post[], older: Post[]): Post[] {
  const ids = new Set(current.map((p) => p.id))
  return [...current, ...older.filter((p) => !ids.has(p.id))]
}

// 知らせを一覧に反映する。acceptCreated：新しい投稿を一覧に足すか（プロフィールでは、自分のときだけ）
export function applyPostEvent(posts: Post[], event: PostEvent, acceptCreated = true): Post[] {
  switch (event.type) {
    case 'created':
      return acceptCreated ? mergeNewer([event.post], posts) : posts
    case 'updated':
      return posts.map((p) => (p.id === event.post.id ? event.post : p))
    case 'liked': {
      const { liked, likeCount } = event
      return posts.map((p) => (p.id === event.id ? { ...p, liked, likeCount } : p))
    }
    case 'commentCount': {
      const { delta } = event
      return posts.map((p) => (p.id === event.id ? { ...p, commentCount: Math.max(0, p.commentCount + delta) } : p))
    }
    case 'deleted':
      return posts.filter((p) => p.id !== event.id)
  }
}
