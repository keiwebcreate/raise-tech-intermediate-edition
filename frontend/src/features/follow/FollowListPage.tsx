import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import * as profileApi from '../profile/profileApi.ts'
import { getFollowList, type FollowListKind } from './followApi.ts'
import { usePagedUsers } from './usePagedUsers.ts'
import { UserListItem } from './UserListItem.tsx'

// 空のときの文言（profile.md 4.5）
const EMPTY_TEXT: Record<FollowListKind, string> = {
  following: 'まだ誰もフォローしていません',
  followers: 'まだフォロワーはいません',
}

type Owner =
  { status: 'loading' } | { status: 'ready'; displayName: string } | { status: 'notFound' } | { status: 'error' }

// S-07 フォロー・フォロワー一覧（/users/:username/following・/followers。profile.md 4.5）。
// 「フォロー中」「フォロワー」の2つのタブ。新しくフォローした順に20件ずつ、下までスクロールで続き
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const { username = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const base = profileApi.profilePath(username)
  // 上の帯に出す表示名。ないユーザーなら「このアカウントは存在しません」
  const [loaded, setLoaded] = useState<{ username: string; owner: Owner } | null>(null)
  const owner: Owner = loaded?.username === username ? loaded.owner : { status: 'loading' }

  useEffect(() => {
    let ignore = false
    profileApi
      .getProfile(username)
      .then((profile) => {
        if (!ignore) setLoaded({ username, owner: { status: 'ready', displayName: profile.displayName } })
      })
      .catch((e: unknown) => {
        if (ignore) return
        const next: Owner = e instanceof ApiError && e.status === 404 ? { status: 'notFound' } : { status: 'error' }
        setLoaded({ username, owner: next })
      })
    return () => {
      ignore = true
    }
  }, [username])

  // アプリの中から来たなら前の画面へ、URL を直接開いたならその人のプロフィールへ
  function goBack() {
    if (location.key !== 'default') navigate(-1)
    else navigate(base)
  }

  return (
    <>
      <Box
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 1,
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1, py: 0.5 }}>
          <IconButton aria-label="戻る" onClick={goBack}>
            <ArrowBackIcon />
          </IconButton>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h6" noWrap sx={{ fontWeight: 700 }}>
              {owner.status === 'ready' ? owner.displayName : kind === 'following' ? 'フォロー中' : 'フォロワー'}
            </Typography>
            <Typography variant="body2" color="text.secondary" noWrap>
              @{username}
            </Typography>
          </Box>
        </Box>
        {/* タブを替えても「戻る」でプロフィールへ戻れるように、履歴を増やさない（replace） */}
        <Tabs value={kind} variant="fullWidth" aria-label="フォロー中・フォロワー">
          <Tab value="following" label="フォロー中" component={RouterLink} to={`${base}/following`} replace />
          <Tab value="followers" label="フォロワー" component={RouterLink} to={`${base}/followers`} replace />
        </Tabs>
      </Box>

      {owner.status === 'notFound' ? (
        <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">
          {MESSAGES.userNotFound}
        </Typography>
      ) : (
        // key：別の人・別のタブに移ったら、一覧を作り直す
        <FollowList key={`${username}:${kind}`} username={username} kind={kind} />
      )}
    </>
  )
}

function FollowList({ username, kind }: { username: string; kind: FollowListKind }) {
  const load = useCallback((before?: number) => getFollowList(username, kind, before), [username, kind])
  const { state, setSentinel, retry } = usePagedUsers(load)

  return (
    <>
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {state.users.map((user) => (
          <UserListItem key={user.id} user={user} />
        ))}
      </Box>
      {state.status === 'loading' && (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 3 }}>
          <CircularProgress size={24} aria-label="読み込み中" />
        </Box>
      )}
      {state.status === 'error' && (
        <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={retry}>もう一度読み込む</Button>}>
          {MESSAGES.loadFailed}
        </Alert>
      )}
      {state.status === 'ready' && state.users.length === 0 && (
        <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
          {EMPTY_TEXT[kind]}
        </Typography>
      )}
      <Box ref={setSentinel} aria-hidden sx={{ height: 1 }} />
    </>
  )
}
