import { describe, expect, it } from 'vitest'
import { applyFollowEvent, followButtonLook } from './followState.ts'

describe('followButtonLook', () => {
  it('フォローしていなければ塗りつぶしの「フォローする」、相手だけがフォローしていれば「フォローバック」', () => {
    expect(followButtonLook(false, false, false)).toEqual({ label: 'フォローする', variant: 'contained' })
    expect(followButtonLook(false, true, false)).toEqual({ label: 'フォローバック', variant: 'contained' })
  })
  it('フォロー中は枠だけの「フォロー中」、マウスを乗せると「フォロー解除」', () => {
    expect(followButtonLook(true, true, false)).toEqual({ label: 'フォロー中', variant: 'outlined' })
    expect(followButtonLook(true, false, true)).toEqual({ label: 'フォロー解除', variant: 'outlined' })
  })
})

describe('applyFollowEvent', () => {
  const other = { id: 2, mine: false, following: false, followingCount: 3, followerCount: 5 }
  const me = { id: 1, mine: true, following: false, followingCount: 3, followerCount: 5 }

  it('その人をフォローしたら、フォロワー数をサーバーの数にし、状態も変える', () => {
    const next = applyFollowEvent(other, { userId: 2, following: true, previous: false, followerCount: 9 })
    expect(next).toMatchObject({ following: true, followerCount: 9, followingCount: 3 })
  })
  it('自分のプロフィールでは、誰かをフォロー・解除したらフォロー数を増やす・減らす', () => {
    expect(applyFollowEvent(me, { userId: 7, following: true, previous: false, followerCount: 1 })).toMatchObject({
      followingCount: 4,
      followerCount: 5,
    })
    expect(applyFollowEvent(me, { userId: 7, following: false, previous: true, followerCount: 0 })).toMatchObject({
      followingCount: 2,
    })
  })
  it('状態が変わらなかった（もうフォロー済みだった）ときは、フォロー数を変えない。関係ない人のプロフィールも変えない', () => {
    expect(applyFollowEvent(me, { userId: 7, following: true, previous: true, followerCount: 1 }).followingCount).toBe(
      3,
    )
    expect(applyFollowEvent(other, { userId: 7, following: true, previous: false, followerCount: 1 })).toBe(other)
  })
})
