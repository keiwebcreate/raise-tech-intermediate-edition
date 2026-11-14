import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import Skeleton from '@mui/material/Skeleton'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { formatJoinedMonth } from '../../shared/time.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { usePostEvents, type PostEvent } from '../post/PostActionsContext.ts'
import type { Post } from '../post/postApi.ts'
import { PostCard } from '../post/PostCard.tsx'
import { appendOlder, applyPostEvent } from '../post/postEvents.ts'
import type { ProfileEditState } from './ProfileEditPage.tsx'
import * as profileApi from './profileApi.ts'

type ProfileState =
  | { status: 'loading' }
  | { status: 'ready'; profile: profileApi.Profile }
  | { status: 'notFound' }
  | { status: 'error' }

// S-05 プロフィール（/users/:username。profile.md 3.1）。
// フォローボタン・「フォローされています」・フォロー数から一覧（S-07）へのリンクは、フォローの機能のときに足す
export function ProfilePage() {
  const { username = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  // 読み込んだ結果。key（どの人の何回目の読み込みか）が今と違えば「読み込み中」とみなす
  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${username}:${reloadCount}`
  const [loaded, setLoaded] = useState<{ key: string; state: ProfileState } | null>(null)
  const state: ProfileState = loaded?.key === requestKey ? loaded.state : { status: 'loading' }

  useEffect(() => {
    let ignore = false // 別の人に移ったあとに届いた返事は使わない
    profileApi
      .getProfile(username)
      .then((profile) => {
        if (!ignore) setLoaded({ key: requestKey, state: { status: 'ready', profile } })
      })
      .catch((e: unknown) => {
        if (ignore) return
        const next: ProfileState =
          e instanceof ApiError && e.status === 404 ? { status: 'notFound' } : { status: 'error' }
        setLoaded({ key: requestKey, state: next })
      })
    return () => {
      ignore = true
    }
  }, [username, requestKey])

  // アプリの中から来たなら前の画面へ、URL を直接開いたならタイムラインへ（投稿の詳細と同じ）
  function goBack() {
    if (location.key !== 'default') navigate(-1)
    else navigate('/')
  }

  const title = state.status === 'ready' ? state.profile.displayName : 'プロフィール'

  return (
    <>
      <Box
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1,
          py: 0.5,
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <IconButton aria-label="戻る" onClick={goBack}>
          <ArrowBackIcon />
        </IconButton>
        <Typography component="h1" variant="h6" noWrap sx={{ fontWeight: 700, minWidth: 0 }}>
          {title}
        </Typography>
      </Box>

      {state.status === 'loading' && <ProfileSkeleton />}
      {state.status === 'notFound' && (
        <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">
          {MESSAGES.userNotFound}
        </Typography>
      )}
      {state.status === 'error' && (
        <Alert
          severity="error"
          sx={{ m: 2 }}
          action={<Button onClick={() => setReloadCount((n) => n + 1)}>もう一度読み込む</Button>}
        >
          {MESSAGES.loadFailed}
        </Alert>
      )}
      {state.status === 'ready' && (
        <>
          <ProfileHeader profile={state.profile} />
          {/* key：別の人に移ったら、投稿の一覧を作り直す */}
          <UserPosts key={requestKey} username={state.profile.username} mine={state.profile.mine} />
        </>
      )}
    </>
  )
}

// アイコン・名前・自己紹介・登録した月・フォロー数
function ProfileHeader({ profile }: { profile: profileApi.Profile }) {
  return (
    <Box
      component="section"
      aria-label="プロフィール"
      sx={{ px: 2, pt: 2, pb: 1.5, borderBottom: 1, borderColor: 'divider' }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <UserAvatar displayName={profile.displayName} iconUrl={profile.iconUrl} size={88} />
        {profile.mine && (
          <Button
            component={RouterLink}
            to="/settings/profile"
            state={{ fromProfile: true } satisfies ProfileEditState}
            variant="outlined"
            sx={{ flexShrink: 0 }}
          >
            プロフィールを編集
          </Button>
        )}
      </Box>
      {/* 見出し（h1）は上の帯の表示名。ここはふつうの文字にして、読み上げで名前の見出しが2回続かないようにする */}
      <Typography component="p" variant="h6" sx={{ fontWeight: 700, mt: 1.5, overflowWrap: 'anywhere' }}>
        {profile.displayName}
      </Typography>
      <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
        @{profile.username}
      </Typography>
      {profile.bio && (
        // 改行はそのまま出す。文字は React が自動でエスケープする（要件 N-26）
        <Typography sx={{ mt: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{profile.bio}</Typography>
      )}
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
        {formatJoinedMonth(profile.createdAt)}
      </Typography>
      {/* 数だけ先に出す。押して一覧（S-07）へ移るのは、フォローの機能のとき */}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 2.5, mt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
            {profile.followingCount}
          </Box>{' '}
          フォロー
        </Typography>
        <Typography variant="body2" color="text.secondary">
          <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
            {profile.followerCount}
          </Box>{' '}
          フォロワー
        </Typography>
      </Box>
    </Box>
  )
}

// key：何回目の読み込みか。今の回と違えば「読み込み中」とみなす（読み直すたびに setState しなくて済む）
type PostsState = { key: number; status: 'loading' | 'ready' | 'error'; posts: Post[]; hasMore: boolean }

// その人の投稿（新しい順に20件ずつ。下までスクロールで続き。表示はタイムラインと同じ）
function UserPosts({ username, mine }: { username: string; mine: boolean }) {
  const [reloadCount, setReloadCount] = useState(0)
  const [loaded, setLoaded] = useState<PostsState | null>(null)
  const state: PostsState = useMemo(
    () => (loaded?.key === reloadCount ? loaded : { key: reloadCount, status: 'loading', posts: [], hasMore: false }),
    [loaded, reloadCount],
  )
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 最初の20件（読み直すときは reloadCount を増やす）
  useEffect(() => {
    let ignore = false
    profileApi
      .getUserPosts(username)
      .then((page) => {
        if (!ignore) setLoaded({ key: reloadCount, status: 'ready', posts: page.posts, hasMore: page.hasMore })
      })
      .catch(() => {
        if (!ignore) setLoaded({ key: reloadCount, status: 'error', posts: [], hasMore: false })
      })
    return () => {
      ignore = true
    }
  }, [username, reloadCount])

  const loadMore = useCallback(async () => {
    const last = state.posts.at(-1)
    if (state.status !== 'ready' || !state.hasMore || loadingMore || !last) return
    setLoadingMore(true)
    setMoreFailed(false)
    try {
      const page = await profileApi.getUserPosts(username, last.id)
      setLoaded((prev) =>
        prev ? { ...prev, posts: appendOlder(prev.posts, page.posts), hasMore: page.hasMore } : prev,
      )
    } catch {
      setMoreFailed(true)
    } finally {
      setLoadingMore(false)
    }
  }, [state, loadingMore, username])

  // 一覧の下の目印が見えたら続きを読む。少し手前（200px）から読み始める
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || moreFailed) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) void loadMore()
      },
      { rootMargin: '200px' },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore, moreFailed])

  // 投稿・編集・削除・いいねを一覧に反映する。新しい投稿は、自分のプロフィールのときだけ足す
  const handleEvent = useCallback(
    (event: PostEvent) => {
      setLoaded((prev) => (prev ? { ...prev, posts: applyPostEvent(prev.posts, event, mine) } : prev))
    },
    [mine],
  )
  usePostEvents(handleEvent)

  return (
    <Box component="section" aria-labelledby="user-posts-title">
      <Typography
        id="user-posts-title"
        component="h2"
        sx={{ fontWeight: 700, px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
      >
        投稿
      </Typography>
      {state.status === 'loading' && (
        <Box sx={{ p: 2 }} aria-label="読み込み中" aria-busy>
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="text" />
          <Skeleton variant="text" width="70%" />
        </Box>
      )}
      {state.status === 'error' && (
        <Alert
          severity="error"
          sx={{ m: 2 }}
          action={<Button onClick={() => setReloadCount((n) => n + 1)}>もう一度読み込む</Button>}
        >
          {MESSAGES.loadFailed}
        </Alert>
      )}
      {state.status === 'ready' && state.posts.length === 0 && (
        <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
          まだ投稿がありません
        </Typography>
      )}
      {state.posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
      {state.status === 'ready' && state.posts.length > 0 && (
        <Box ref={sentinelRef} sx={{ py: 3, display: 'flex', justifyContent: 'center' }}>
          {moreFailed ? (
            <Alert severity="error" action={<Button onClick={() => setMoreFailed(false)}>もう一度読み込む</Button>}>
              {MESSAGES.loadFailed}
            </Alert>
          ) : state.hasMore ? (
            <CircularProgress size={24} aria-label="読み込み中" />
          ) : (
            <Typography variant="body2" color="text.secondary">
              これ以上はありません
            </Typography>
          )}
        </Box>
      )}
    </Box>
  )
}

// 読み込み中の仮の表示（screen-design.md 3.5）
function ProfileSkeleton() {
  return (
    <Box sx={{ px: 2, pt: 2, pb: 1.5 }} aria-label="読み込み中" aria-busy>
      <Skeleton variant="circular" width={88} height={88} />
      <Skeleton variant="text" width="40%" sx={{ mt: 1.5, fontSize: '1.25rem' }} />
      <Skeleton variant="text" width="25%" />
      <Skeleton variant="text" width="70%" sx={{ mt: 1.5 }} />
    </Box>
  )
}
