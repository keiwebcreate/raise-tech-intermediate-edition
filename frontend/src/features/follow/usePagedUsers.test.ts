// ユーザーの一覧を20件ずつ読むフック（like.md 4.3・profile.md 4.5・user-search.md 3章）
import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { listedUser } from '../../test/data.ts'
import { FakeIntersectionObserver } from '../../test/intersection.ts'
import { usePagedUsers, type UsersChunk } from './usePagedUsers.ts'

// 一番下の目印を置いて、それが画面に入ったことにする
function scrollToBottom(setSentinel: (element: HTMLElement | null) => void) {
  act(() => setSentinel(document.createElement('div')))
  act(() => FakeIntersectionObserver.intersect())
}

describe('usePagedUsers', () => {
  it('開いたら最初の20件を目印なしで読み、読み終えたら ready になる', async () => {
    const load = vi.fn(async (): Promise<UsersChunk> => ({ users: [listedUser(1), listedUser(2)], nextBefore: 10 }))
    const { result } = renderHook(() => usePagedUsers(load))

    expect(result.current.state.status).toBe('loading')
    await waitFor(() => expect(result.current.state.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(1)
    expect(load.mock.calls[0][0]).toBeUndefined()
    expect(result.current.state.users.map((user) => user.id)).toEqual([1, 2])
    expect(result.current.state.next).toBe(10)
  })

  it('検索の続きの目印（nextOffset）も next になる', async () => {
    const load = vi.fn(async (): Promise<UsersChunk> => ({ users: [listedUser(1)], nextOffset: 20 }))
    const { result } = renderHook(() => usePagedUsers(load))

    await waitFor(() => expect(result.current.state.next).toBe(20))
  })

  it('一番下が見えたら、目印を渡して続きを読み、下に足す', async () => {
    const load = vi.fn(async (next?: number): Promise<UsersChunk> =>
      next === undefined
        ? { users: [listedUser(1), listedUser(2)], nextBefore: 2 }
        : { users: [listedUser(3)], nextBefore: null },
    )
    const { result } = renderHook(() => usePagedUsers(load))
    await waitFor(() => expect(result.current.state.status).toBe('ready'))

    scrollToBottom(result.current.setSentinel)

    await waitFor(() => expect(result.current.state.users.map((user) => user.id)).toEqual([1, 2, 3]))
    expect(load).toHaveBeenLastCalledWith(2)
    expect(result.current.state.next).toBeNull()
  })

  it('続きがなければ（next が null）、一番下が見えても読まない', async () => {
    const load = vi.fn(async (): Promise<UsersChunk> => ({ users: [listedUser(1)], nextBefore: null }))
    const { result } = renderHook(() => usePagedUsers(load))
    await waitFor(() => expect(result.current.state.status).toBe('ready'))

    scrollToBottom(result.current.setSentinel)

    expect(load).toHaveBeenCalledTimes(1)
  })

  it('読み直しで同じ人が来ても、2回は出さない（検索の途中でフォロワー数が変わったとき。user-search.md 7章）', async () => {
    const load = vi.fn(async (next?: number): Promise<UsersChunk> =>
      next === undefined
        ? { users: [listedUser(1), listedUser(2)], nextOffset: 2 }
        : { users: [listedUser(2), listedUser(3)], nextOffset: null },
    )
    const { result } = renderHook(() => usePagedUsers(load))
    await waitFor(() => expect(result.current.state.status).toBe('ready'))

    scrollToBottom(result.current.setSentinel)

    await waitFor(() => expect(result.current.state.users.map((user) => user.id)).toEqual([1, 2, 3]))
  })

  it('読めなかったら error になり、retry でもう一度読む', async () => {
    const load = vi
      .fn<(next?: number) => Promise<UsersChunk>>()
      .mockRejectedValueOnce(new Error('つながらない'))
      .mockResolvedValueOnce({ users: [listedUser(1)], nextBefore: null })
    const { result } = renderHook(() => usePagedUsers(load))
    await waitFor(() => expect(result.current.state.status).toBe('error'))

    act(() => result.current.retry())

    await waitFor(() => expect(result.current.state.status).toBe('ready'))
    expect(result.current.state.users.map((user) => user.id)).toEqual([1])
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('続きが読めなかったら、それまでの人は残したまま error になる', async () => {
    const load = vi
      .fn<(next?: number) => Promise<UsersChunk>>()
      .mockResolvedValueOnce({ users: [listedUser(1)], nextBefore: 1 })
      .mockRejectedValueOnce(new Error('つながらない'))
    const { result } = renderHook(() => usePagedUsers(load))
    await waitFor(() => expect(result.current.state.status).toBe('ready'))

    scrollToBottom(result.current.setSentinel)

    await waitFor(() => expect(result.current.state.status).toBe('error'))
    expect(result.current.state.users.map((user) => user.id)).toEqual([1])
  })
})
