// フォローの表示の決まり（docs/features/profile.md 4章）。画面の部品から切り離して、テストできるようにしてある

// フォロー・解除が済んだ知らせ。プロフィールはこれを受けて、フォロー数・フォロワー数をそろえる
export type FollowEvent = {
  userId: number
  following: boolean
  // 押す前の状態（数を増やすか減らすかを決める）
  previous: boolean
  // 相手の今のフォロワー数（サーバーの返事）
  followerCount: number
}

// ボタンの文字と見た目（4.3）。hovering：フォロー中のボタンにマウスを乗せている・キーボードで選んでいる
export function followButtonLook(
  following: boolean,
  followedBy: boolean,
  hovering: boolean,
): { label: string; variant: 'contained' | 'outlined' } {
  if (following) return { label: hovering ? 'フォロー解除' : 'フォロー中', variant: 'outlined' }
  return { label: followedBy ? 'フォローバック' : 'フォローする', variant: 'contained' }
}

type Counts = { id: number; mine: boolean; following: boolean; followingCount: number; followerCount: number }

// 知らせをプロフィールの数に反映する。
// - そのプロフィールの人をフォロー・解除した：フォロワー数をサーバーの数に、ボタンの状態も変える
// - 自分のプロフィール：誰かをフォロー・解除したら、フォロー数を1つ増やす・減らす
export function applyFollowEvent<T extends Counts>(profile: T, event: FollowEvent): T {
  let next = profile
  if (profile.id === event.userId) {
    next = { ...next, following: event.following, followerCount: event.followerCount }
  }
  if (profile.mine && event.following !== event.previous) {
    next = { ...next, followingCount: Math.max(0, next.followingCount + (event.following ? 1 : -1)) }
  }
  return next
}
