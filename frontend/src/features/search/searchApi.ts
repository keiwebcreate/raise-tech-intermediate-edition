// ユーザー検索の API（docs/features/user-search.md 8章）
import { api } from '../../api/client.ts'
import type { ListedUser } from '../follow/followApi.ts'

export type SearchPage = {
  // 一致の順位・フォロワーの多い順（20件まで）。1人の形はフォローの一覧と同じ
  users: ListedUser[]
  // 続きを読むときに offset に入れる値。続きがなければ null
  nextOffset: number | null
}

// q：検索の言葉（そろえるのはサーバー）。offset：何人目から読むか（最初は 0）
export async function searchUsers(q: string, offset?: number): Promise<SearchPage> {
  return (await api.get<SearchPage>('/api/users', { params: { q, offset } })).data
}
