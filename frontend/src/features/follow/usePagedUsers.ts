// ユーザーの一覧を20件ずつ読み、一番下が見えたら続きを読む（いいねしたユーザー M-02・フォロー中とフォロワー S-07）
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ListedUser, UsersPage } from './followApi.ts'

export type PagedUsers = {
  users: ListedUser[]
  nextBefore: number | null
  status: 'loading' | 'ready' | 'error'
}

// load：続き（before）を渡すと20件返す。load が変わったら（別の人・別のタブ）、使う側で key を変えて作り直す
export function usePagedUsers(load: (before?: number) => Promise<UsersPage>) {
  const [state, setState] = useState<PagedUsers>({ users: [], nextBefore: null, status: 'loading' })
  const loadingRef = useRef(false)
  const [sentinel, setSentinel] = useState<HTMLElement | null>(null)

  const loadMore = useCallback(
    async (before?: number) => {
      if (loadingRef.current) return
      loadingRef.current = true
      setState((prev) => ({ ...prev, status: 'loading' }))
      try {
        const page = await load(before)
        setState((prev) => ({
          // 続きのときは後ろに足す。同じ人を2回出さない
          users: [...prev.users, ...page.users.filter((u) => !prev.users.some((p) => p.id === u.id))],
          nextBefore: page.nextBefore,
          status: 'ready',
        }))
      } catch {
        setState((prev) => ({ ...prev, status: 'error' }))
      } finally {
        loadingRef.current = false
      }
    },
    [load],
  )

  // 最初の20件（状態は最初から「読み込み中」なので、返事が来てから書き換える）
  useEffect(() => {
    let ignore = false
    load()
      .then((page) => {
        if (!ignore) setState({ users: page.users, nextBefore: page.nextBefore, status: 'ready' })
      })
      .catch(() => {
        if (!ignore) setState((prev) => ({ ...prev, status: 'error' }))
      })
    return () => {
      ignore = true
    }
  }, [load])

  // 一覧の一番下（sentinel）が見えたら、続きを読む
  useEffect(() => {
    if (!sentinel || state.nextBefore === null || state.status !== 'ready') return
    const next = state.nextBefore
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore(next)
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [sentinel, state.nextBefore, state.status, loadMore])

  // 失敗したときの「もう一度読み込む」
  const retry = useCallback(() => void loadMore(state.nextBefore ?? undefined), [loadMore, state.nextBefore])

  return { state, setSentinel, retry }
}
