// プロフィールの API（docs/features/profile.md 8章）
import { api } from '../../api/client.ts'
import type { TimelinePage } from '../post/postApi.ts'

export type Profile = {
  id: number
  username: string
  displayName: string
  // 自己紹介。なければ null
  bio: string | null
  // アイコンを見るための期限付きの URL。未設定なら null
  iconUrl: string | null
  // アイコンの S3 のキー。編集で「残す」ときに、そのまま送り返す
  iconKey: string | null
  // 登録した日時
  createdAt: string
  followingCount: number
  followerCount: number
  // 見ている人のプロフィールか
  mine: boolean
}

export type ProfileInput = {
  displayName: string
  bio: string | null
  // 今のキー＝残す／uploadIcon のキー＝差し替える／null＝外す
  iconKey: string | null
}

// プロフィールの URL（S-05）。ユーザー名に使えるのは英数字と _ だけだが、念のため URL 用に変える
export function profilePath(username: string): string {
  return `/users/${encodeURIComponent(username)}`
}

// 名前のついたユーザーが同じ人か（ユーザー名は大文字・小文字を区別しない。profile.md 8章）
export function sameUsername(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase()
}

export async function getProfile(username: string): Promise<Profile> {
  return (await api.get<Profile>(`/api/users/${encodeURIComponent(username)}`)).data
}

// その人の投稿。before：この投稿より古いもの（続き）
export async function getUserPosts(username: string, before?: number): Promise<TimelinePage> {
  return (await api.get<TimelinePage>(`/api/users/${encodeURIComponent(username)}/posts`, { params: { before } })).data
}

// アイコンにする画像を送る。サーバーが正方形の 400×400 の WebP にし、一時置き場に置く。signal で途中でやめられる
export async function uploadIcon(file: File, signal: AbortSignal): Promise<{ key: string; url: string }> {
  const form = new FormData()
  form.append('file', file)
  return (await api.post<{ key: string; url: string }>('/api/media/icons', form, { signal })).data
}

export async function updateProfile(input: ProfileInput): Promise<Profile> {
  return (await api.put<Profile>('/api/users/me', input)).data
}
