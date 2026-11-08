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
import { useCallback, useEffect, useRef, useState } from 'react'
import { MESSAGES } from '../../shared/messages.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import type { Liker, LikersPage } from './likeApi.ts'

type Props = {
  // 続き（before）を渡すと、いいねした人を20件返す
  load: (before?: number) => Promise<LikersPage>
  onClose: () => void
}

type State = {
  users: Liker[]
  nextBefore: number | null
  status: 'loading' | 'ready' | 'error'
}

// いいねしたユーザー（M-02、like.md 4.3）。新しくいいねした順に20件ずつ、下までスクロールで続きを読む。
// フォローボタン・自己紹介・プロフィールへのリンクは、フォロー・プロフィールの機能のときに足す
export function LikersDialog({ load, onClose }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const [state, setState] = useState<State>({ users: [], nextBefore: null, status: 'loading' })
  const loadingRef = useRef(false)
  const [sentinel, setSentinel] = useState<HTMLElement | null>(null)

  const loadMore = useCallback(
    async (before?: number) => {
      if (loadingRef.current) return
      loadingRef.current = true
      setState((prev) => ({ ...prev, status: 'loading' }))
      try {
        const page = await load(before)
        setState((prev) => ({
          // 続きのときは後ろに足す。同じ人を2回出さない
          users: [...prev.users, ...page.users.filter((u) => !prev.users.some((p) => p.id === u.id))],
          nextBefore: page.nextBefore,
          status: 'ready',
        }))
      } catch {
        setState((prev) => ({ ...prev, status: 'error' }))
      } finally {
        loadingRef.current = false
      }
    },
    [load],
  )

  // 開いたら最初の20件（状態は最初から「読み込み中」なので、返事が来てから書き換える）
  useEffect(() => {
    let ignore = false
    load()
      .then((page) => {
        if (!ignore) setState({ users: page.users, nextBefore: page.nextBefore, status: 'ready' })
      })
      .catch(() => {
        if (!ignore) setState((prev) => ({ ...prev, status: 'error' }))
      })
    return () => {
      ignore = true
    }
  }, [load])

  // 一覧の一番下が見えたら、続きを読む
  useEffect(() => {
    if (!sentinel || state.nextBefore === null || state.status !== 'ready') return
    const next = state.nextBefore
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore(next)
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [sentinel, state.nextBefore, state.status, loadMore])

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
            <Box component="li" key={user.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 1.25 }}>
              <UserAvatar displayName={user.displayName} />
              <Box sx={{ minWidth: 0 }}>
                <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{user.displayName}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                  @{user.username}
                </Typography>
              </Box>
            </Box>
          ))}
        </Box>
        {state.status === 'loading' && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 2 }}>
            <CircularProgress size={24} aria-label="読み込み中" />
          </Box>
        )}
        {state.status === 'error' && (
          <Alert
            severity="error"
            sx={{ m: 2 }}
            action={<Button onClick={() => void loadMore(state.nextBefore ?? undefined)}>もう一度読み込む</Button>}
          >
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
