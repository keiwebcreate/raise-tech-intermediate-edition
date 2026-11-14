import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router'
import type { UserSummary } from '../../api/types.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { profilePath } from './profileApi.ts'

type Props = {
  user: UserSummary
  // 押したときにほかにしたいこと（ダイアログを閉じるなど）
  onClick?: () => void
}

// アイコン。押すとプロフィールへ（features.md US-02）。
// すぐ横の名前のリンクと行き先が同じなので、キーボードの移動と読み上げでは飛ばす（同じリンクが2回続かないように）
export function AvatarLink({ user, onClick, size }: Props & { size?: number }) {
  return (
    <Box
      component={RouterLink}
      to={profilePath(user.username)}
      onClick={onClick}
      tabIndex={-1}
      aria-hidden
      sx={{ display: 'block', flexShrink: 0, alignSelf: 'flex-start', borderRadius: '50%' }}
    >
      <UserAvatar displayName={user.displayName} iconUrl={user.iconUrl} size={size} />
    </Box>
  )
}

// 表示名と @ユーザー名。押すとプロフィールへ。stacked：2行に分ける（一覧のとき）
export function UserNameLink({ user, onClick, stacked = false }: Props & { stacked?: boolean }) {
  return (
    <Link
      component={RouterLink}
      to={profilePath(user.username)}
      onClick={onClick}
      color="inherit"
      underline="none"
      sx={{
        display: stacked ? 'block' : 'inline-flex',
        flexWrap: 'wrap',
        columnGap: 0.75,
        minWidth: 0,
        overflowWrap: 'anywhere',
        // 名前だけに下線を出す（@ユーザー名には付けない）
        '&:hover .user-name, &.Mui-focusVisible .user-name': { textDecoration: 'underline' },
      }}
    >
      <Typography
        component="span"
        className="user-name"
        sx={{ fontWeight: 700, display: stacked ? 'block' : 'inline' }}
      >
        {user.displayName}
      </Typography>{' '}
      <Typography
        component="span"
        color="text.secondary"
        variant={stacked ? 'body2' : 'body1'}
        sx={{ display: stacked ? 'block' : 'inline' }}
      >
        @{user.username}
      </Typography>
    </Link>
  )
}
