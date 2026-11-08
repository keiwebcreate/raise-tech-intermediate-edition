import { describe, expect, it, vi } from 'vitest'
import { createLikeToggler, type LikeState } from './likeToggler.ts'

// 返事を手で返せる send（テストの中で、いつ返事が来るかを決められる）
function controllableSend() {
  const calls: { liked: boolean; resolve: (s: LikeState) => void; reject: () => void }[] = []
  const send = (liked: boolean) =>
    new Promise<LikeState>((resolve, reject) => {
      calls.push({ liked, resolve, reject: () => reject(new Error('失敗')) })
    })
  return { send, calls }
}

// 返事のあとの処理（then）が終わるのを待つ
const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('createLikeToggler', () => {
  it('押したらすぐ表示が変わり、返事が来たらサーバーの数にそろう', async () => {
    const { send, calls } = controllableSend()
    const onSettled = vi.fn()
    const toggler = createLikeToggler({ liked: false, likeCount: 2 }, send, { onSettled, onError: vi.fn() })

    expect(toggler.toggle()).toEqual({ liked: true, likeCount: 3 })
    expect(calls.map((c) => c.liked)).toEqual([true])

    // ほかの人も同時にいいねしていたので、サーバーの数は 4
    calls[0].resolve({ liked: true, likeCount: 4 })
    await settle()
    expect(onSettled).toHaveBeenCalledWith({ liked: true, likeCount: 4 })
  })

  it('連打しても送信は1つずつで、最後に押した状態になる', async () => {
    const { send, calls } = controllableSend()
    const onSettled = vi.fn()
    const toggler = createLikeToggler({ liked: false, likeCount: 0 }, send, { onSettled, onError: vi.fn() })

    toggler.toggle() // いいね（送る）
    toggler.toggle() // 取り消し（送信中なので覚えておく）
    expect(toggler.toggle()).toEqual({ liked: true, likeCount: 1 }) // もう一度いいね
    expect(calls).toHaveLength(1)

    // 最初の送信（いいね）の返事。最後に押した状態（いいね）と同じなので、もう送らない
    calls[0].resolve({ liked: true, likeCount: 1 })
    await settle()
    expect(calls).toHaveLength(1)
    expect(onSettled).toHaveBeenLastCalledWith({ liked: true, likeCount: 1 })
  })

  it('送っている間に取り消したら、返事のあとで取り消しを送る', async () => {
    const { send, calls } = controllableSend()
    const onSettled = vi.fn()
    const toggler = createLikeToggler({ liked: false, likeCount: 0 }, send, { onSettled, onError: vi.fn() })

    toggler.toggle()
    toggler.toggle()
    calls[0].resolve({ liked: true, likeCount: 1 })
    await settle()
    expect(calls.map((c) => c.liked)).toEqual([true, false])
    expect(onSettled).not.toHaveBeenCalled()

    calls[1].resolve({ liked: false, likeCount: 0 })
    await settle()
    expect(onSettled).toHaveBeenCalledWith({ liked: false, likeCount: 0 })
  })

  it('失敗したら、サーバーで確かめられた最後の状態に戻す', async () => {
    const { send, calls } = controllableSend()
    const onError = vi.fn()
    const toggler = createLikeToggler({ liked: true, likeCount: 5 }, send, { onSettled: vi.fn(), onError })

    expect(toggler.toggle()).toEqual({ liked: false, likeCount: 4 })
    calls[0].reject()
    await settle()
    expect(onError).toHaveBeenCalledWith({ liked: true, likeCount: 5 })

    // 戻したあとは、また押せる
    expect(toggler.toggle()).toEqual({ liked: false, likeCount: 4 })
  })

  it('送信中は外からの状態を受け入れず、送信していなければ受け入れる', async () => {
    const { send, calls } = controllableSend()
    const toggler = createLikeToggler({ liked: false, likeCount: 0 }, send, { onSettled: vi.fn(), onError: vi.fn() })

    toggler.toggle()
    expect(toggler.sync({ liked: false, likeCount: 9 })).toBe(false)
    calls[0].resolve({ liked: true, likeCount: 1 })
    await settle()
    expect(toggler.sync({ liked: true, likeCount: 7 })).toBe(true)
    expect(toggler.toggle()).toEqual({ liked: false, likeCount: 6 })
  })
})
