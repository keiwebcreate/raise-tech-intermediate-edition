import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError } from '../../api/client.ts'
import { ConfirmDialog } from '../../shared/ConfirmDialog.tsx'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import { PostActionsContext, type PostEvent } from './PostActionsContext.ts'
import * as postApi from './postApi.ts'
import { PostDialog } from './PostDialog.tsx'

// 投稿のモーダル（M-01）と削除の確認（M-03）を持ち、どの画面からでも開けるようにする。
// 投稿・編集・削除ができたら、タイムラインや投稿の詳細に知らせる（PostEvent）
export function PostActionsProvider({ children }: { children: ReactNode }) {
  const notify = useNotify()
  // undefined：閉じている ／ null：新しく投稿 ／ Post：その投稿を編集
  const [dialog, setDialog] = useState<postApi.Post | null | undefined>(undefined)
  const [deleting, setDeleting] = useState<postApi.Post | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const listeners = useRef(new Set<(event: PostEvent) => void>())

  const emit = useCallback((event: PostEvent) => {
    listeners.current.forEach((listener) => listener(event))
  }, [])

  const subscribe = useCallback((listener: (event: PostEvent) => void) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  async function handleSubmit(content: string, media: string[]) {
    if (dialog) {
      const post = await postApi.updatePost(dialog.id, content, media)
      emit({ type: 'updated', post })
      notify(MESSAGES.postUpdated)
    } else {
      const post = await postApi.createPost(content, media)
      emit({ type: 'created', post })
      notify(MESSAGES.posted)
    }
    setDialog(undefined)
  }

  async function handleDelete() {
    if (!deleting) return
    setDeleteBusy(true)
    try {
      await postApi.deletePost(deleting.id)
      notify(MESSAGES.postDeleted)
      emit({ type: 'deleted', id: deleting.id })
    } catch (e) {
      if (e instanceof ApiError && e.code === 'POST_NOT_FOUND') {
        // もう消えていた。画面からも消す
        notify(MESSAGES.postNotFound)
        emit({ type: 'deleted', id: deleting.id })
      } else {
        notify(e instanceof ApiError ? e.detail : MESSAGES.network)
      }
    } finally {
      setDeleteBusy(false)
      setDeleting(null)
    }
  }

  const value = useMemo(
    () => ({
      openCreate: () => setDialog(null),
      openEdit: (post: postApi.Post) => setDialog(post),
      requestDelete: (post: postApi.Post) => setDeleting(post),
      emit,
      subscribe,
    }),
    [emit, subscribe],
  )

  return (
    <PostActionsContext value={value}>
      {children}
      {dialog !== undefined && (
        // key：開くたびに入力欄を作り直す（前の入力が残らないように）
        <PostDialog
          key={dialog?.id ?? 'new'}
          editing={dialog}
          onSubmit={handleSubmit}
          onClose={() => setDialog(undefined)}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        title="この投稿を削除しますか？"
        message="コメントといいねも消えます"
        confirmLabel="削除する"
        danger
        busy={deleteBusy}
        onConfirm={handleDelete}
        onCancel={() => setDeleting(null)}
      />
    </PostActionsContext>
  )
}
