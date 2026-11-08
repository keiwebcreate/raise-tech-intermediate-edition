import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Skeleton from '@mui/material/Skeleton'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { usePostEvents, type PostEvent } from './PostActionsContext.ts'
import * as postApi from './postApi.ts'
import { PostCard } from './PostCard.tsx'

type State =
  { status: 'loading' } | { status: 'ready'; post: postApi.Post } | { status: 'notFound' } | { status: 'error' }

// S-04 投稿の詳細（/posts/:id）。コメントは、コメントの機能を作るときに足す
export function PostDetailPage() {
  const { id } = useParams()
  const postId = Number(id)
  const navigate = useNavigate()
  const location = useLocation()
  const validId = Number.isInteger(postId) && postId > 0
  // 読み込んだ結果。key（どの投稿の何回目の読み込みか）が今と違えば「読み込み中」とみなす
  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${postId}:${reloadCount}`
  const [loaded, setLoaded] = useState<{ key: string; state: State } | null>(null)
  const state: State = !validId
    ? { status: 'notFound' }
    : loaded?.key === requestKey
      ? loaded.state
      : { status: 'loading' }

  useEffect(() => {
    if (!validId) return
    let ignore = false // 別の投稿に移ったあとに届いた返事は使わない
    postApi
      .getPost(postId)
      .then((post) => {
        if (!ignore) setLoaded({ key: requestKey, state: { status: 'ready', post } })
      })
      .catch((e: unknown) => {
        if (ignore) return
        const next: State = e instanceof ApiError && e.status === 404 ? { status: 'notFound' } : { status: 'error' }
        setLoaded({ key: requestKey, state: next })
      })
    return () => {
      ignore = true
    }
  }, [postId, validId, requestKey])

  // 編集したら表示を直し、消したらタイムラインへ戻る（post.md 4.5）
  const handleEvent = useCallback(
    (event: PostEvent) => {
      if (event.type === 'updated' && event.post.id === postId) {
        setLoaded({ key: requestKey, state: { status: 'ready', post: event.post } })
      }
      if (event.type === 'liked' && event.id === postId) {
        const { liked, likeCount } = event
        setLoaded((prev) =>
          prev && prev.state.status === 'ready'
            ? { ...prev, state: { ...prev.state, post: { ...prev.state.post, liked, likeCount } } }
            : prev,
        )
      }
      if (event.type === 'deleted' && event.id === postId) navigate('/', { replace: true })
    },
    [postId, requestKey, navigate],
  )
  usePostEvents(handleEvent)

  // アプリの中から来たなら前の画面へ、URL を直接開いたならタイムラインへ
  function goBack() {
    if (location.key !== 'default') navigate(-1)
    else navigate('/')
  }

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
        <Typography component="h1" variant="h6" sx={{ fontWeight: 700 }}>
          投稿
        </Typography>
      </Box>
      {state.status === 'loading' && (
        <Box sx={{ p: 2 }}>
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="text" />
          <Skeleton variant="text" width="80%" />
        </Box>
      )}
      {state.status === 'ready' && <PostCard post={state.post} variant="detail" />}
      {state.status === 'notFound' && (
        <Typography sx={{ p: 4, textAlign: 'center' }} color="text.secondary">
          {MESSAGES.postNotFound}
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
    </>
  )
}
