// 投稿の詳細のコメント欄に出しているもの（読み込んだコメント・返信と数）を、変えたときにどう直すか
// （docs/features/comment.md 4章）。画面の部品から切り離して、テストできるようにしてある。
// どの関数も、元の値は変えずに新しい値を返す（React の state はこの形で変える）

import type { LikeState } from '../like/likeToggler.ts'
import type { Comment, CommentsPage } from './commentApi.ts'

// 投稿の詳細のコメントの入力欄の id。投稿の 💬 を押したときに、ここへカーソルを移す
export const COMMENT_INPUT_ID = 'comment-input'

export type CommentsState = {
  // 投稿への直接のコメント（古い順）
  top: Comment[]
  // 続きを読むときの after。続きがなければ null
  nextAfter: number | null
  // 一番上のコメントの ID ごとの返信（古い順）。まだ読んでいなければ入っていない
  replies: Record<number, Comment[]>
}

export function initialState(page: CommentsPage): CommentsState {
  return { top: page.comments, nextAfter: page.nextAfter, replies: {} }
}

// 「さらに表示」で読んだ続きを足す。
// 先に自分で足したコメントが続きの中にもあれば、前のほうから外して、続きの中の正しい位置に置く
export function appendPage(state: CommentsState, page: CommentsPage): CommentsState {
  const ids = new Set(page.comments.map((c) => c.id))
  return {
    ...state,
    top: [...state.top.filter((c) => !ids.has(c.id)), ...page.comments],
    nextAfter: page.nextAfter,
  }
}

// 返信を読んだ
export function setReplies(state: CommentsState, rootId: number, replies: Comment[]): CommentsState {
  return { ...state, replies: { ...state.replies, [rootId]: replies } }
}

// コメント・返信を書いた。数を1つ増やし、並びの一番下に足す（comment.md 4.1・4.5）
export function addComment(state: CommentsState, comment: Comment): CommentsState {
  const rootId = comment.rootCommentId
  const parentId = comment.parentCommentId
  if (rootId === null || parentId === null) {
    if (state.top.some((c) => c.id === comment.id)) return state
    return { ...state, top: [...state.top, comment] }
  }
  const replies = state.replies[rootId]
  return {
    ...state,
    // 一番上のコメント：下に並ぶ返信が1つ増える。直接の返信なら、直接の返信の数も増える
    top: state.top.map((c) =>
      c.id === rootId
        ? { ...c, threadReplyCount: c.threadReplyCount + 1, replyCount: c.replyCount + (parentId === rootId ? 1 : 0) }
        : c,
    ),
    // 返信をまだ読んでいなければ、開いたときに読むので足さない
    replies:
      replies === undefined
        ? state.replies
        : {
            ...state.replies,
            [rootId]: [...bump(replies, parentId, 1), comment],
          },
  }
}

// コメント・返信を消した。下に付いた返信も一緒に消え、数を減らす（comment.md 4.4）。
// 返事の removedTop は、投稿に直接付いたコメントを消したか（投稿のコメント数を減らすため）
export function removeComment(state: CommentsState, id: number): { state: CommentsState; removedTop: boolean } {
  if (state.top.some((c) => c.id === id)) {
    const replies = { ...state.replies }
    delete replies[id]
    return { state: { ...state, top: state.top.filter((c) => c.id !== id), replies }, removedTop: true }
  }
  for (const [key, list] of Object.entries(state.replies)) {
    const target = list.find((c) => c.id === id)
    if (!target) continue
    const rootId = Number(key)
    // 消す返信と、その下に付いた返信すべて（返信は古い順なので、前から順に「親が消えるもの」を集めればよい）
    const removed = new Set([id])
    for (const c of list) {
      if (c.parentCommentId !== null && removed.has(c.parentCommentId)) removed.add(c.id)
    }
    const parentId = target.parentCommentId
    return {
      state: {
        ...state,
        top: state.top.map((c) =>
          c.id === rootId
            ? {
                ...c,
                threadReplyCount: c.threadReplyCount - removed.size,
                replyCount: c.replyCount - (parentId === rootId ? 1 : 0),
              }
            : c,
        ),
        replies: {
          ...state.replies,
          [rootId]: bump(
            list.filter((c) => !removed.has(c.id)),
            parentId,
            -1,
          ),
        },
      },
      removedTop: false,
    }
  }
  return { state, removedTop: false }
}

// いいね・取り消しを送り終えた。表示をサーバーの数にそろえる
export function updateLike(state: CommentsState, id: number, like: LikeState): CommentsState {
  const apply = (c: Comment) => (c.id === id ? { ...c, liked: like.liked, likeCount: like.likeCount } : c)
  const replies: Record<number, Comment[]> = {}
  for (const [key, list] of Object.entries(state.replies)) replies[Number(key)] = list.map(apply)
  return { ...state, top: state.top.map(apply), replies }
}

// 「返信先：@ユーザー名」を出すか。返信への返信のときだけ（直接の返信は、上のコメントへの返信だと分かるので出さない）
export function showsReplyTo(comment: Comment): boolean {
  return comment.parentCommentId !== null && comment.parentCommentId !== comment.rootCommentId
}

// 返信の中の1件（返信先）の「直接の返信の数」を増やす・減らす
function bump(list: Comment[], id: number | null, delta: number): Comment[] {
  return list.map((c) => (c.id === id ? { ...c, replyCount: c.replyCount + delta } : c))
}
