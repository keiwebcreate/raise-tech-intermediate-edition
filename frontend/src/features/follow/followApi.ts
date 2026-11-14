// フォローの API（docs/features/profile.md 8章）
import { api } from '../../api/client.ts'
import type { UserSummary } from '../../api/types.ts'

// フォローの状態つきのユーザー（プロフィール・一覧・いいねした人・コメントを書いた人）
export type FollowState = {
  // 自分がこの人をフォローしているか
  following: boolean
  // この人が自分をフォローしているか（「フォローされています」・「フォローバック」）
  followedBy: boolean
}

export type FollowableUser = UserSummary & FollowState

// 一覧（フォロー中・フォロワー・いいねした人）の1人
export type ListedUser = FollowableUser & {
  // 自己紹介。なければ null（1行まで出す）
  bio: string | null
}

export type UsersPage = {
  users: ListedUser[]
  // 続きを読むときに before に入れる値。続きがなければ null
  nextBefore: number | null
}

export type FollowResult = {
  following: boolean
  // 相手の今のフォロワー数
  followerCount: number
}

// following が true ならフォロー（PUT）、false なら解除（DELETE）。どちらも何回送っても同じ結果
export async function setFollow(username: string, following: boolean): Promise<FollowResult> {
  const url = `/api/users/${encodeURIComponent(username)}/follow`
  return (following ? await api.put<FollowResult>(url) : await api.delete<FollowResult>(url)).data
}

export type FollowListKind = 'following' | 'followers'

// その人がフォローしている人（following）・その人のフォロワー（followers）。新しい順に20件
export async function getFollowList(username: string, kind: FollowListKind, before?: number): Promise<UsersPage> {
  return (await api.get<UsersPage>(`/api/users/${encodeURIComponent(username)}/${kind}`, { params: { before } })).data
}
