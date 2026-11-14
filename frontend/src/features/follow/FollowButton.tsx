import Button from '@mui/material/Button'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext.ts'
import { useFollowActions, useFollowing } from './FollowContext.ts'
import { followButtonLook } from './followState.ts'

type Props = {
  user: { id: number; username: string }
  // サーバーから来たフォローの状態
  following: boolean
  followedBy: boolean
  size?: 'small' | 'medium'
}

// フォローボタン（共通の部品。profile.md 4.3）。自分には出さない。
// 「フォローする」「フォローバック」は塗りつぶし、「フォロー中」は枠だけで、マウスを乗せると「フォロー解除」
export function FollowButton({ user, following: fromServer, followedBy, size = 'medium' }: Props) {
  const { user: me } = useAuth()
  const { toggle } = useFollowActions()
  const { following, pending } = useFollowing(user.id, fromServer)
  const [hovering, setHovering] = useState(false)
  if (!me || me.id === user.id) return null
  const look = followButtonLook(following, followedBy, hovering)

  return (
    <Button
      variant={look.variant}
      size={size}
      // マウスを乗せている間・キーボードで選んでいる間は「フォロー解除」（赤）にする
      color={following && hovering ? 'error' : 'primary'}
      disabled={pending}
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      onFocus={() => setHovering(true)}
      onBlur={() => setHovering(false)}
      onClick={() => {
        // フォローした直後は、マウスが乗ったままでも「フォロー中」と出す（すぐ「フォロー解除」にしない）
        setHovering(false)
        toggle(user, following)
      }}
      // 一覧では同じ文字のボタンが並ぶので、読み上げの名前に @ユーザー名 を足す（見えている文字から始める）
      aria-label={`${look.label} @${user.username}`}
      // 文字が変わっても幅が変わらないように（「フォロー解除」が一番長い）
      sx={{ flexShrink: 0, minWidth: size === 'small' ? 104 : 120, whiteSpace: 'nowrap' }}
    >
      {look.label}
    </Button>
  )
}
