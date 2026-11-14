import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { AvatarLink, UserNameLink } from '../profile/UserLinks.tsx'
import type { ListedUser } from './followApi.ts'
import { FollowButton } from './FollowButton.tsx'

type Props = {
  user: ListedUser
  // 名前・アイコンを押したときにほかにしたいこと（ダイアログを閉じるなど）
  onNavigate?: () => void
}

// 一覧の1人（フォロー中・フォロワーの一覧 S-07、いいねしたユーザー M-02。profile.md 4.5・like.md 4.3）。
// アイコン・表示名・@ユーザー名・「フォローされています」・自己紹介（1行まで）・フォローボタン
export function UserListItem({ user, onNavigate }: Props) {
  return (
    <Box component="li" sx={{ display: 'flex', gap: 1.5, px: 2, py: 1.25 }}>
      <AvatarLink user={user} onClick={onNavigate} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <UserNameLink user={user} onClick={onNavigate} stacked />
            {user.followedBy && <FollowedByBadge />}
          </Box>
          <FollowButton user={user} following={user.following} followedBy={user.followedBy} size="small" />
        </Box>
        {user.bio && (
          <Typography variant="body2" noWrap title={user.bio} sx={{ mt: 0.25 }}>
            {user.bio}
          </Typography>
        )}
      </Box>
    </Box>
  )
}

// 「フォローされています」（相手が自分をフォローしているとき。profile.md 3.1・4.5）
export function FollowedByBadge() {
  return (
    <Typography
      component="span"
      variant="caption"
      sx={{
        display: 'inline-block',
        px: 0.75,
        borderRadius: 1,
        bgcolor: 'action.selected',
        color: 'text.secondary',
        whiteSpace: 'nowrap',
      }}
    >
      フォローされています
    </Typography>
  )
}
