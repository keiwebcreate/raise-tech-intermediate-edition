import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Skeleton from '@mui/material/Skeleton'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client.ts'
import { ConfirmDialog } from '../../shared/ConfirmDialog.tsx'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { useAuth } from '../auth/AuthContext.ts'
import * as commentApi from './commentApi.ts'
import type { Comment } from './commentApi.ts'
import { CommentComposer } from './CommentComposer.tsx'
import { CommentItem } from './CommentItem.tsx'
import {
  addComment,
  appendPage,
  COMMENT_INPUT_ID,
  initialState,
  removeComment,
  setReplies,
  updateLike,
  type CommentsState,
} from './commentState.ts'

type Props = {
  postId: number
  // 投稿への直接のコメントが増えた・減った（投稿のコメント数をそろえる）
  onCountChange: (delta: number) => void
}

// 投稿の詳細（S-04）のコメント欄（comment.md 4章）。
// 投稿への直接のコメントを古い順に50件ずつ。返信は「返信を表示（N件）」で開き、1段だけ字下げして並べる
export function CommentSection({ postId, onCountChange }: Props) {
  const { user } = useAuth()
  const notify = useNotify()
  const [state, setState] = useState<CommentsState | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  // 返信を開いているコメントと、返信の読み込みの状態
  const [openThreads, setOpenThreads] = useState<ReadonlySet<number>>(new Set())
  const [replyStatus, setReplyStatus] = useState<Record<number, 'loading' | 'error'>>({})
  // 返信の入力欄を開いているコメント（1つだけ）と、入力しかけの内容があるか
  const [replyingTo, setReplyingTo] = useState<Comment | null>(null)
  const [replyDirty, setReplyDirty] = useState(false)
  // 入力しかけの返信を捨ててよいか確かめている間の、次に開くもの（null なら閉じるだけ）
  const [discarding, setDiscarding] = useState<{ next: Comment | null } | null>(null)
  const [deleting, setDeleting] = useState<Comment | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)

  // 最初の50件（postId ごとに部品を作り直すので、読み込み中の表示は state が null のとき）
  useEffect(() => {
    let ignore = false
    commentApi
      .getComments(postId)
      .then((page) => {
        if (!ignore) setState(initialState(page))
      })
      .catch(() => {
        if (!ignore) setLoadError(true)
      })
    return () => {
      ignore = true
    }
  }, [postId])

  // 最初の読み込みに失敗したあとの「もう一度読み込む」
  function retry() {
    setLoadError(false)
    commentApi
      .getComments(postId)
      .then((page) => setState(initialState(page)))
      .catch(() => setLoadError(true))
  }

  async function loadMore() {
    if (!state || state.nextAfter === null) return
    setLoadingMore(true)
    try {
      const page = await commentApi.getComments(postId, state.nextAfter)
      setState((prev) => (prev ? appendPage(prev, page) : prev))
    } catch {
      notify(MESSAGES.loadFailed)
    } finally {
      setLoadingMore(false)
    }
  }

  async function loadReplies(rootId: number) {
    setReplyStatus((prev) => ({ ...prev, [rootId]: 'loading' }))
    try {
      const replies = await commentApi.getReplies(rootId)
      setState((prev) => (prev ? setReplies(prev, rootId, replies) : prev))
      setReplyStatus((prev) => withoutKey(prev, rootId))
    } catch {
      setReplyStatus((prev) => ({ ...prev, [rootId]: 'error' }))
    }
  }

  function openThread(rootId: number) {
    setOpenThreads((prev) => new Set(prev).add(rootId))
    if (!state?.replies[rootId]) void loadReplies(rootId)
  }

  function closeThread(rootId: number) {
    setOpenThreads((prev) => {
      const next = new Set(prev)
      next.delete(rootId)
      return next
    })
  }

  // 投稿へのコメント
  async function submitComment(content: string) {
    const comment = await commentApi.createComment(postId, content)
    setState((prev) => (prev ? addComment(prev, comment) : prev))
    onCountChange(1)
  }

  // 返信。送ったら入力欄を閉じ、返信の並びを開く（comment.md 4.5）
  async function submitReply(parent: Comment, content: string) {
    const reply = await commentApi.createComment(postId, content, parent.id)
    const rootId = reply.rootCommentId ?? parent.id
    setState((prev) => (prev ? addComment(prev, reply) : prev))
    setReplyingTo(null)
    setReplyDirty(false)
    openThread(rootId)
  }

  // 「返信」を押した。入力しかけの別の返信があれば、捨ててよいか確かめる
  function requestReply(target: Comment) {
    if (replyingTo?.id === target.id) return
    if (replyingTo && replyDirty) setDiscarding({ next: target })
    else startReply(target)
  }

  function startReply(target: Comment | null) {
    setReplyingTo(target)
    setReplyDirty(false)
  }

  function cancelReply() {
    if (replyDirty) setDiscarding({ next: null })
    else startReply(null)
  }

  async function handleDelete() {
    if (!deleting) return
    const id = deleting.id
    setDeleteBusy(true)
    try {
      await commentApi.deleteComment(id)
      removeFromScreen(id)
      notify(MESSAGES.commentDeleted)
    } catch (e) {
      if (e instanceof ApiError && e.code === 'COMMENT_NOT_FOUND') {
        // もう消えていた。画面からも消す
        removeFromScreen(id)
        notify(e.detail)
      } else {
        notify(e instanceof ApiError ? e.detail : MESSAGES.network)
      }
    } finally {
      setDeleteBusy(false)
      setDeleting(null)
    }
  }

  // 削除の通信を待っている間に、いいねの返事や「さらに表示」の続きが届いていることがある。
  // 古い state に上書きしないように、最新の state（prev）から消す
  function removeFromScreen(id: number) {
    if (state?.top.some((c) => c.id === id)) onCountChange(-1)
    setState((prev) => (prev ? removeComment(prev, id).state : prev))
    // 消したコメント（か、その上）に返信していたら、入力欄も閉じる
    if (replyingTo && (replyingTo.id === id || isUnder(replyingTo, id))) startReply(null)
  }

  // reply が、消したコメント（id）の下に付いた返信か（一番上のコメントを消したときと、返信の返信を消したとき）
  function isUnder(reply: Comment, id: number): boolean {
    if (reply.rootCommentId === id) return true
    const list = reply.rootCommentId === null ? undefined : state?.replies[reply.rootCommentId]
    if (!list) return false
    // 返信先を上へたどる
    let parentId = reply.parentCommentId
    while (parentId !== null) {
      if (parentId === id) return true
      parentId = list.find((c) => c.id === parentId)?.parentCommentId ?? null
    }
    return false
  }

  function handleLikeSettled(id: number, like: { liked: boolean; likeCount: number }) {
    setState((prev) => (prev ? updateLike(prev, id, like) : prev))
  }

  // 1件と、その下の返信の入力欄
  function renderItem(comment: Comment) {
    return (
      <CommentItem comment={comment} onReply={requestReply} onDelete={setDeleting} onLikeSettled={handleLikeSettled}>
        {replyingTo?.id === comment.id && (
          <Box sx={{ mt: 1 }}>
            <CommentComposer
              inputId={`reply-input-${comment.id}`}
              placeholder="返信を入力"
              submitLabel="返信する"
              replyToUsername={comment.author.username}
              onSubmit={(content) => submitReply(comment, content)}
              onChange={(value) => setReplyDirty(value !== '')}
              onCancel={cancelReply}
              focusOnOpen
            />
          </Box>
        )}
      </CommentItem>
    )
  }

  return (
    <Box component="section" aria-label="コメント" sx={{ borderTop: 1, borderColor: 'divider' }}>
      <Box sx={{ display: 'flex', gap: 1.5, px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        {user && <UserAvatar displayName={user.displayName} size={36} />}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <CommentComposer
            inputId={COMMENT_INPUT_ID}
            placeholder="コメントを入力"
            submitLabel="コメントする"
            onSubmit={submitComment}
          />
        </Box>
      </Box>

      {state === null && !loadError && (
        <Box sx={{ p: 2 }} aria-label="読み込み中">
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="text" />
        </Box>
      )}
      {state === null && loadError && (
        <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={retry}>もう一度読み込む</Button>}>
          {MESSAGES.loadFailed}
        </Alert>
      )}
      {state && state.top.length === 0 && (
        <Typography color="text.secondary" sx={{ p: 2 }}>
          まだコメントはありません
        </Typography>
      )}
      {state && state.top.length > 0 && (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {state.top.map((top) => {
            const open = openThreads.has(top.id)
            const replies = state.replies[top.id]
            const status = replyStatus[top.id]
            return (
              <Box component="li" key={top.id} sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}>
                {renderItem(top)}
                {top.threadReplyCount > 0 && (
                  <Button
                    size="small"
                    startIcon={open ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    aria-expanded={open}
                    onClick={() => (open ? closeThread(top.id) : openThread(top.id))}
                    sx={{ ml: 5.5, mb: 1 }}
                  >
                    {open ? '返信を隠す' : `返信を表示（${top.threadReplyCount}件）`}
                  </Button>
                )}
                {open && (
                  // 返信は1段だけ字下げして、返信の返信も同じ段に並べる（comment.md 4.2）
                  <Box sx={{ ml: { xs: 2, sm: 5.5 }, pl: 1.5, borderLeft: 2, borderColor: 'divider', mb: 1 }}>
                    {replies && (
                      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                        {replies.map((reply) => (
                          <Box component="li" key={reply.id}>
                            {renderItem(reply)}
                          </Box>
                        ))}
                      </Box>
                    )}
                    {status === 'loading' && (
                      <Box sx={{ display: 'grid', placeItems: 'center', py: 1 }}>
                        <CircularProgress size={20} aria-label="返信を読み込み中" />
                      </Box>
                    )}
                    {status === 'error' && (
                      <Alert
                        severity="error"
                        sx={{ my: 1 }}
                        action={<Button onClick={() => void loadReplies(top.id)}>もう一度読み込む</Button>}
                      >
                        {MESSAGES.loadFailed}
                      </Alert>
                    )}
                  </Box>
                )}
              </Box>
            )
          })}
        </Box>
      )}
      {state && state.nextAfter !== null && (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 1.5 }}>
          <Button onClick={() => void loadMore()} disabled={loadingMore}>
            {loadingMore ? '読み込み中…' : 'さらに表示'}
          </Button>
        </Box>
      )}

      <ConfirmDialog
        open={discarding !== null}
        title="編集内容を破棄しますか？"
        confirmLabel="破棄する"
        danger
        onConfirm={() => {
          startReply(discarding?.next ?? null)
          setDiscarding(null)
        }}
        onCancel={() => setDiscarding(null)}
      />
      <ConfirmDialog
        open={deleting !== null}
        title="このコメントを削除しますか？"
        // 下に返信があれば、一緒に消えることを伝える（screen-design.md M-03）
        message={deleting && deleting.replyCount > 0 ? 'このコメントへの返信も消えます' : undefined}
        confirmLabel="削除する"
        danger
        busy={deleteBusy}
        onConfirm={() => void handleDelete()}
        onCancel={() => setDeleting(null)}
      />
    </Box>
  )
}

function withoutKey<T>(record: Record<number, T>, key: number): Record<number, T> {
  const next = { ...record }
  delete next[key]
  return next
}
