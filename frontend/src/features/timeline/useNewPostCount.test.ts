// 新しい投稿の数を60秒ごとに確かめるフック（timeline.md 3.2）
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getNewPostCount } from '../post/postApi.ts'
import { NEW_POST_POLL_MS, useNewPostCount } from './useNewPostCount.ts'

vi.mock('../post/postApi.ts', () => ({ getNewPostCount: vi.fn() }))

const countApi = vi.mocked(getNewPostCount)

// ブラウザのタブが見えているか（document.visibilityState）を決める
function setVisibility(state: DocumentVisibilityState) {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
}

// 時計を進め、そのあいだの API の返事も受け取りきる
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

describe('useNewPostCount', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    countApi.mockReset()
    countApi.mockResolvedValue(3)
    setVisibility('visible')
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('確かめる間隔は60秒', () => {
    expect(NEW_POST_POLL_MS).toBe(60_000)
  })

  it('60秒たつと、今のタブと一番上の投稿で数を聞き、count に入る', async () => {
    const { result } = renderHook(() => useNewPostCount('following', 42, true))

    await advance(NEW_POST_POLL_MS)

    expect(countApi).toHaveBeenCalledWith('following', 42)
    expect(result.current.count).toBe(3)
  })

  it('60秒ごとに聞き続ける', async () => {
    renderHook(() => useNewPostCount('all', 42, true))
    await advance(NEW_POST_POLL_MS)
    const afterFirst = countApi.mock.calls.length

    await advance(NEW_POST_POLL_MS * 2)

    expect(countApi.mock.calls.length).toBe(afterFirst + 2)
  })

  it('enabled が false のあいだは聞かない', async () => {
    renderHook(() => useNewPostCount('all', 42, false))

    await advance(NEW_POST_POLL_MS * 3)

    expect(countApi).not.toHaveBeenCalled()
  })

  it('ブラウザのタブが隠れているあいだは聞かない', async () => {
    setVisibility('hidden')
    renderHook(() => useNewPostCount('all', 42, true))

    await advance(NEW_POST_POLL_MS * 3)

    expect(countApi).not.toHaveBeenCalled()
  })

  it('隠れていたタブが見えるようになったら、60秒を待たずにすぐ1回聞く', async () => {
    setVisibility('hidden')
    const { result } = renderHook(() => useNewPostCount('all', 42, true))
    await advance(NEW_POST_POLL_MS)

    setVisibility('visible')
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(countApi).toHaveBeenCalledTimes(1)
    expect(result.current.count).toBe(3)
  })

  it('reset で 0 に戻る（「新しい投稿 N件」を押したあと）', async () => {
    const { result } = renderHook(() => useNewPostCount('all', 42, true))
    await advance(NEW_POST_POLL_MS)
    expect(result.current.count).toBe(3)

    act(() => result.current.reset())

    expect(result.current.count).toBe(0)
  })

  it('タブが変わったら、前のタブの数は 0 に戻し、新しいタブで聞き直す', async () => {
    const { result, rerender } = renderHook(({ tab }) => useNewPostCount(tab, 42, true), {
      initialProps: { tab: 'all' as 'all' | 'following' },
    })
    await advance(NEW_POST_POLL_MS)
    expect(result.current.count).toBe(3)

    rerender({ tab: 'following' })
    expect(result.current.count).toBe(0)

    await advance(NEW_POST_POLL_MS)
    expect(countApi).toHaveBeenLastCalledWith('following', 42)
  })

  it('聞くのに失敗しても、画面は壊れず次の60秒でまた聞く', async () => {
    countApi.mockRejectedValueOnce(new Error('つながらない'))
    const { result } = renderHook(() => useNewPostCount('all', 42, true))
    await advance(NEW_POST_POLL_MS)
    expect(result.current.count).toBe(0)

    await advance(NEW_POST_POLL_MS)

    expect(result.current.count).toBe(3)
  })
})
