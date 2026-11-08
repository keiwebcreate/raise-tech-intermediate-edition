import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Skeleton from '@mui/material/Skeleton'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import { readStorage, writeStorage } from '../../shared/storage.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { usePostActions, usePostEvents, type PostEvent } from '../post/PostActionsContext.ts'
import * as postApi from '../post/postApi.ts'
import { PostCard } from '../post/PostCard.tsx'
import { PostComposer } from '../post/PostComposer.tsx'
import { useNewPostCount } from './useNewPostCount.ts'

const TAB_STORAGE_KEY = 'rt-tab'

// 読み込んだ一覧。key は「どのタブの何回目の読み込みか」。今の key と違えば「読み込み中」とみなす
// （読み込みを始めるたびに setState しなくて済む）
type ListState = {
  key: string
  status: 'loading' | 'ready' | 'error'
  posts: postApi.Post[]
  hasMore: boolean
}

function initialTab(): postApi.Tab {
  return readStorage(TAB_STORAGE_KEY) === 'following' ? 'following' : 'all'
}

// 同じ投稿を2回出さない（新しい投稿を足したときなど）
function mergeNewer(newer: postApi.Post[], current: postApi.Post[]): postApi.Post[] {
  const ids = new Set(newer.map((p) => p.id))
  return [...newer, ...current.filter((p) => !ids.has(p.id))]
}

// S-03 タイムライン（docs/features/timeline.md）
export function TimelinePage() {
  const { user } = useAuth()
  const { emit } = usePostActions()
  const notify = useNotify()
  const [tab, setTab] = useState<postApi.Tab>(initialTab)
  const [reloadCount, setReloadCount] = useState(0)
  const listKey = `${tab}:${reloadCount}`
  const [loaded, setLoaded] = useState<ListState | null>(null)
  const list: ListState = useMemo(
    () => (loaded?.key === listKey ? loaded : { key: listKey, status: 'loading', posts: [], hasMore: false }),
    [loaded, listKey],
  )
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [loadingNewer, setLoadingNewer] = useState(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 最初から読み直す（タブを押し直したとき・失敗したとき・新しい投稿が多すぎるとき）
  function reload() {
    setMoreFailed(false)
    setReloadCount((n) => n + 1)
  }

  // タブを開いたとき・読み直すときに、最新の20件を読む
  useEffect(() => {
    let ignore = false // 別のタブに移ったあとに届いた返事は使わない
    postApi
      .getTimeline(tab)
      .then((page) => {
        if (!ignore) setLoaded({ key: listKey, status: 'ready', posts: page.posts, hasMore: page.hasMore })
      })
      .catch(() => {
        if (!ignore) setLoaded({ key: listKey, status: 'error', posts: [], hasMore: false })
      })
    return () => {
      ignore = true
    }
  }, [tab, listKey])

  // 読み込んだ一覧を直す（今のタブの一覧のときだけ）
  const updatePosts = useCallback(
    (change: (prev: ListState) => Partial<ListState>) => {
      setLoaded((prev) => (prev?.key === listKey ? { ...prev, ...change(prev) } : prev))
    },
    [listKey],
  )

  // 続きの20件（TL-03）
  const loadMore = useCallback(async () => {
    const last = list.posts.at(-1)
    if (list.status !== 'ready' || !list.hasMore || loadingMore || !last) return
    setLoadingMore(true)
    setMoreFailed(false)
    try {
      const page = await postApi.getTimeline(tab, { before: last.id })
      updatePosts((prev) => {
        const ids = new Set(prev.posts.map((p) => p.id))
        return { posts: [...prev.posts, ...page.posts.filter((p) => !ids.has(p.id))], hasMore: page.hasMore }
      })
    } catch {
      setMoreFailed(true)
    } finally {
      setLoadingMore(false)
    }
  }, [list, loadingMore, tab, updatePosts])

  // 一覧の下の目印が見えたら（下までスクロールしたら）続きを読む。少し手前（200px）から読み始める
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

  // 新しい投稿の知らせ（60秒ごと）
  const topId = list.posts[0]?.id
  const newPosts = useNewPostCount(tab, topId, list.status === 'ready')

  // 「新しい投稿 N件」を押したら、一番上に足す。多すぎて抜けが出るときは、一覧を読み直す
  async function showNewer() {
    setLoadingNewer(true)
    try {
      if (topId === undefined) {
        reload()
        return
      }
      const page = await postApi.getTimeline(tab, { after: topId })
      if (page.hasMore) {
        reload()
      } else {
        updatePosts((prev) => ({ posts: mergeNewer(page.posts, prev.posts) }))
      }
      newPosts.reset()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch {
      // 失敗したら、ボタンを残しておく（もう一度押せる）
    } finally {
      setLoadingNewer(false)
    }
  }

  // 投稿・編集・削除を一覧に反映する
  const handleEvent = useCallback(
    (event: PostEvent) => {
      updatePosts((prev) => {
        if (event.type === 'created') return { posts: mergeNewer([event.post], prev.posts) }
        if (event.type === 'updated') {
          return { posts: prev.posts.map((p) => (p.id === event.post.id ? event.post : p)) }
        }
        return { posts: prev.posts.filter((p) => p.id !== event.id) }
      })
    },
    [updatePosts],
  )
  usePostEvents(handleEvent)

  function changeTab(next: postApi.Tab) {
    writeStorage(TAB_STORAGE_KEY, next)
    if (next === tab) {
      reload() // 押し直したら最新を読む
    } else {
      setMoreFailed(false)
      setTab(next)
    }
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
        <Typography
          component="h1"
          sx={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}
        >
          ホーム
        </Typography>
        <Tabs value={tab} onChange={(_, value: postApi.Tab) => changeTab(value)} variant="fullWidth">
          <Tab value="all" label="すべて" onClick={() => tab === 'all' && changeTab('all')} />
          <Tab value="following" label="フォロー中" onClick={() => tab === 'following' && changeTab('following')} />
        </Tabs>
        {newPosts.count > 0 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 1, borderTop: 1, borderColor: 'divider' }}>
            <Button variant="contained" size="small" onClick={showNewer} disabled={loadingNewer}>
              ↑ 新しい投稿 {newPosts.count >= 100 ? '100件以上' : `${newPosts.count}件`}
            </Button>
          </Box>
        )}
      </Box>

      {/* 上の入力欄（P-01）。スマホでは右下の「投稿する」ボタンから投稿する */}
      <Box
        sx={{ display: { xs: 'none', sm: 'flex' }, gap: 1.5, px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
      >
        {user && <UserAvatar displayName={user.displayName} />}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <PostComposer
            inputId="timeline-composer"
            submitLabel="投稿する"
            clearOnSuccess
            onSubmit={async (content, media) => {
              emit({ type: 'created', post: await postApi.createPost(content, media) })
              notify(MESSAGES.posted)
            }}
          />
        </Box>
      </Box>

      {list.status === 'loading' && <TimelineSkeleton />}

      {list.status === 'error' && (
        <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={reload}>もう一度読み込む</Button>}>
          {MESSAGES.loadFailed}
        </Alert>
      )}

      {list.status === 'ready' && list.posts.length === 0 && <EmptyTimeline tab={tab} />}

      {list.posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}

      {list.status === 'ready' && list.posts.length > 0 && (
        <Box ref={sentinelRef} sx={{ py: 3, display: 'flex', justifyContent: 'center' }}>
          {moreFailed ? (
            <Alert severity="error" action={<Button onClick={() => setMoreFailed(false)}>もう一度読み込む</Button>}>
              {MESSAGES.loadFailed}
            </Alert>
          ) : list.hasMore ? (
            <CircularProgress size={24} aria-label="読み込み中" />
          ) : (
            <Typography variant="body2" color="text.secondary">
              これ以上はありません
            </Typography>
          )}
        </Box>
      )}
    </>
  )
}

// 読み込み中の仮の表示（screen-design.md 3.5）
function TimelineSkeleton() {
  return (
    <Box aria-label="読み込み中" aria-busy>
      {[0, 1, 2].map((i) => (
        <Box key={i} sx={{ display: 'flex', gap: 1.5, px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <Skeleton variant="circular" width={40} height={40} />
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="40%" />
            <Skeleton variant="text" />
            <Skeleton variant="text" width="70%" />
          </Box>
        </Box>
      ))}
    </Box>
  )
}

// 空のとき（timeline.md 3章）。検索の画面はまだないので、リンクは検索の機能を作るときに足す
function EmptyTimeline({ tab }: { tab: postApi.Tab }) {
  return (
    <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
      {tab === 'all'
        ? 'まだ投稿がありません'
        : 'まだ誰もフォローしていません。『すべて』や検索から、気になる人を探してみましょう'}
    </Typography>
  )
}
