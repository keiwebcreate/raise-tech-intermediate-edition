// テストで使う見本のデータ。必要なところだけ上書きして使う
import type { ListedUser } from '../features/follow/followApi.ts'
import type { Post } from '../features/post/postApi.ts'

export function listedUser(id: number, change: Partial<ListedUser> = {}): ListedUser {
  return {
    id,
    username: `user${id}`,
    displayName: `ユーザー ${id}`,
    iconUrl: null,
    bio: null,
    following: false,
    followedBy: false,
    ...change,
  }
}

export function post(id: number, change: Partial<Post> = {}): Post {
  return {
    id,
    content: `投稿 ${id} の本文`,
    createdAt: '2026-11-21T10:00:00+09:00',
    editedAt: null,
    author: { id: 2, username: 'hanako', displayName: '佐藤 花子', iconUrl: null },
    mine: false,
    media: [],
    likeCount: 0,
    liked: false,
    commentCount: 0,
    ...change,
  }
}
