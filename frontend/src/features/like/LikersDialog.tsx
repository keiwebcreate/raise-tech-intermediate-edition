import CloseIcon from '@mui/icons-material/Close'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { MESSAGES } from '../../shared/messages.ts'
import { usePagedUsers } from '../follow/usePagedUsers.ts'
import { UserListItem } from '../follow/UserListItem.tsx'
import type { LikersPage } from './likeApi.ts'

type Props = {
  // 続き（before）を渡すと、いいねした人を20件返す
  load: (before?: number) => Promise<LikersPage>
  onClose: () => void
}

// いいねしたユーザー（M-02、like.md 4.3）。新しくいいねした順に20件ずつ、下までスクロールで続きを読む。
// 1人にアイコン・名前（押すとプロフィールへ）・「フォローされています」・自己紹介（1行）・フォローボタン
export function LikersDialog({ load, onClose }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const { state, setSentinel, retry } = usePagedUsers(load)

  return (
    <Dialog open onClose={onClose} fullScreen={fullScreen} fullWidth maxWidth="xs" aria-labelledby="likers-title">
      {/* DialogTitle はダイアログの名前の id を自分に付けてしまうので使わない（PostDialog と同じ） */}
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
      >
        <IconButton aria-label="閉じる" onClick={onClose}>
          <CloseIcon />
        </IconButton>
        <Typography id="likers-title" component="h2" variant="h6" sx={{ fontWeight: 700 }}>
          いいねしたユーザー
        </Typography>
      </Box>
      <DialogContent sx={{ p: 0 }}>
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {state.users.map((user) => (
            // 押すとプロフィールへ。同じ人のプロフィールの上で開いていても閉じるように、押したら閉じる
            <UserListItem key={user.id} user={user} onNavigate={onClose} />
          ))}
        </Box>
        {state.status === 'loading' && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 2 }}>
            <CircularProgress size={24} aria-label="読み込み中" />
          </Box>
        )}
        {state.status === 'error' && (
          <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={retry}>もう一度読み込む</Button>}>
            {MESSAGES.loadFailed}
          </Alert>
        )}
        {state.status === 'ready' && state.users.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            まだいいねはありません
          </Typography>
        )}
        <Box ref={setSentinel} aria-hidden sx={{ height: 1 }} />
      </DialogContent>
    </Dialog>
  )
}
