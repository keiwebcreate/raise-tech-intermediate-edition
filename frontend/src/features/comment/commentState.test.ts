import { describe, expect, it } from 'vitest'
import type { Comment } from './commentApi.ts'
import {
  addComment,
  appendPage,
  initialState,
  removeComment,
  setReplies,
  showsReplyTo,
  updateLike,
  type CommentsState,
} from './commentState.ts'

// テスト用のコメント。parent・root を渡すと返信になる
function comment(id: number, parent: number | null = null, root: number | null = null): Comment {
  return {
    id,
    postId: 1,
    parentCommentId: parent,
    rootCommentId: root,
    content: `コメント${id}`,
    createdAt: '2026-11-14T00:00:00Z',
    author: { id: 1, username: 'taro', displayName: '太郎' },
    replyTo: parent === null ? null : { username: 'hanako' },
    mine: true,
    likeCount: 0,
    liked: false,
    replyCount: 0,
    threadReplyCount: 0,
  }
}

const find = (state: CommentsState, id: number) =>
  [...state.top, ...Object.values(state.replies).flat()].find((c) => c.id === id)

// コメント1 の下に、返信2（1への返信）と返信3（2への返信）がある状態
function thread(): CommentsState {
  const top = { ...comment(1), replyCount: 1, threadReplyCount: 2 }
  const state = initialState({ comments: [top], nextAfter: null })
  return setReplies(state, 1, [{ ...comment(2, 1, 1), replyCount: 1 }, comment(3, 2, 1)])
}

describe('appendPage', () => {
  it('続きを後ろに足し、先に自分で足したコメントは続きの中の位置に移す', () => {
    let state = initialState({ comments: [comment(1)], nextAfter: 1 })
    state = addComment(state, comment(3))
    state = appendPage(state, { comments: [comment(2), comment(3)], nextAfter: null })
    expect(state.top.map((c) => c.id)).toEqual([1, 2, 3])
    expect(state.nextAfter).toBeNull()
  })
})

describe('addComment', () => {
  it('投稿へのコメントは一番下に足す（同じものは2回足さない）', () => {
    let state = initialState({ comments: [comment(1)], nextAfter: null })
    state = addComment(state, comment(2))
    state = addComment(state, comment(2))
    expect(state.top.map((c) => c.id)).toEqual([1, 2])
  })

  it('直接の返信：一番上のコメントの「返信 N」と「返信を表示（N件）」の両方が増える', () => {
    let state = setReplies(initialState({ comments: [comment(1)], nextAfter: null }), 1, [])
    state = addComment(state, comment(2, 1, 1))
    expect(find(state, 1)).toMatchObject({ replyCount: 1, threadReplyCount: 1 })
    expect(state.replies[1].map((c) => c.id)).toEqual([2])
  })

  it('返信への返信：返信先の「返信 N」と、一番上の「返信を表示（N件）」が増える', () => {
    const state = addComment(thread(), comment(4, 3, 1))
    expect(find(state, 1)).toMatchObject({ replyCount: 1, threadReplyCount: 3 })
    expect(find(state, 3)).toMatchObject({ replyCount: 1 })
    expect(state.replies[1].map((c) => c.id)).toEqual([2, 3, 4])
  })

  it('返信をまだ読んでいなければ、数だけ増やす（開いたときに読む）', () => {
    const state = addComment(initialState({ comments: [comment(1)], nextAfter: null }), comment(2, 1, 1))
    expect(find(state, 1)).toMatchObject({ threadReplyCount: 1 })
    expect(state.replies[1]).toBeUndefined()
  })
})

describe('removeComment', () => {
  it('投稿へのコメントを消すと、返信も一緒に消える', () => {
    const { state, removedTop } = removeComment(thread(), 1)
    expect(removedTop).toBe(true)
    expect(state.top).toEqual([])
    expect(state.replies[1]).toBeUndefined()
  })

  it('返信を消すと、その下の返信も消え、数が減る', () => {
    const { state, removedTop } = removeComment(thread(), 2)
    expect(removedTop).toBe(false)
    expect(state.replies[1]).toEqual([])
    expect(find(state, 1)).toMatchObject({ replyCount: 0, threadReplyCount: 0 })
  })

  it('返信への返信を消すと、返信先の「返信 N」が減る', () => {
    const { state } = removeComment(thread(), 3)
    expect(state.replies[1].map((c) => c.id)).toEqual([2])
    expect(find(state, 2)).toMatchObject({ replyCount: 0 })
    expect(find(state, 1)).toMatchObject({ replyCount: 1, threadReplyCount: 1 })
  })
})

describe('updateLike', () => {
  it('コメントでも返信でも、いいねの数をそろえる', () => {
    let state = updateLike(thread(), 1, { liked: true, likeCount: 3 })
    state = updateLike(state, 3, { liked: true, likeCount: 1 })
    expect(find(state, 1)).toMatchObject({ liked: true, likeCount: 3 })
    expect(find(state, 3)).toMatchObject({ liked: true, likeCount: 1 })
  })
})

describe('showsReplyTo', () => {
  it('「返信先：@」は返信への返信のときだけ出す', () => {
    expect(showsReplyTo(comment(1))).toBe(false)
    expect(showsReplyTo(comment(2, 1, 1))).toBe(false)
    expect(showsReplyTo(comment(3, 2, 1))).toBe(true)
  })
})
