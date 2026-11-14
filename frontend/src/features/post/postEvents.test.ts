import { describe, expect, it } from 'vitest'
import type { Post } from './postApi.ts'
import { appendOlder, applyPostEvent, mergeNewer } from './postEvents.ts'

function post(id: number, change: Partial<Post> = {}): Post {
  return {
    id,
    content: `投稿 ${id}`,
    createdAt: '2026-11-15T00:00:00Z',
    editedAt: null,
    author: { id: 1, username: 'taro', displayName: '太郎', iconUrl: null },
    mine: true,
    media: [],
    likeCount: 0,
    liked: false,
    commentCount: 0,
    ...change,
  }
}

const ids = (posts: Post[]) => posts.map((p) => p.id)

describe('mergeNewer・appendOlder', () => {
  it('上・下に足し、同じ投稿は2回出さない', () => {
    expect(ids(mergeNewer([post(5), post(4)], [post(4), post(3)]))).toEqual([5, 4, 3])
    expect(ids(appendOlder([post(5), post(4)], [post(4), post(3)]))).toEqual([5, 4, 3])
  })
})

describe('applyPostEvent', () => {
  const list = [post(3), post(2), post(1)]

  it('投稿したら一番上に足す。acceptCreated が false なら足さない', () => {
    expect(ids(applyPostEvent(list, { type: 'created', post: post(4) }))).toEqual([4, 3, 2, 1])
    expect(ids(applyPostEvent(list, { type: 'created', post: post(4) }, false))).toEqual([3, 2, 1])
  })
  it('編集したら差し替え、削除したら外す', () => {
    const updated = applyPostEvent(list, { type: 'updated', post: post(2, { content: '直した' }) })
    expect(updated[1]?.content).toBe('直した')
    expect(ids(applyPostEvent(list, { type: 'deleted', id: 2 }))).toEqual([3, 1])
  })
  it('いいね数とコメント数をそろえる。コメント数は 0 より小さくしない', () => {
    const liked = applyPostEvent(list, { type: 'liked', id: 1, liked: true, likeCount: 7 })
    expect(liked[2]).toMatchObject({ liked: true, likeCount: 7 })
    const counted = applyPostEvent(list, { type: 'commentCount', id: 3, delta: -1 })
    expect(counted[0]?.commentCount).toBe(0)
  })
})
