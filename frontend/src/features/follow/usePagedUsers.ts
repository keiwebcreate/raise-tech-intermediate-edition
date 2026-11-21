// ユーザーの一覧を20件ずつ読み、一番下が見えたら続きを読む（いいねしたユーザー M-02・フォロー中とフォロワー S-07・ユーザー検索 S-08）
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ListedUser } from './followApi.ts'

// 1回に読んだ20件と、続きの目印。目印は一覧によって違う：
// フォロー・いいねした人は nextBefore（この ID より前）、検索は nextOffset（何人目から）。どちらも、なければ null
export type UsersChunk = {
  users: ListedUser[]
  nextBefore?: number | null
  nextOffset?: number | null
}

export type PagedUsers = {
  users: ListedUser[]
  // 続きを読むときに load に渡す目印。続きがなければ null
  next: number | null
  status: 'loading' | 'ready' | 'error'
}

function nextOf(chunk: UsersChunk): number | null {
  return chunk.nextBefore ?? chunk.nextOffset ?? null
}

// load：続きの目印を渡すと20件返す（渡さなければ最初の20件）。
// load が変わったら（別の人・別のタブ・別の言葉）、使う側で key を変えて作り直す
export function usePagedUsers(load: (next?: number) => Promise<UsersChunk>) {
  const [state, setState] = useState<PagedUsers>({ users: [], next: null, status: 'loading' })
  const loadingRef = useRef(false)
  const [sentinel, setSentinel] = useState<HTMLElement | null>(null)

  const loadMore = useCallback(
    async (next?: number) => {
      if (loadingRef.current) return
      loadingRef.current = true
      setState((prev) => ({ ...prev, status: 'loading' }))
      try {
        const chunk = await load(next)
        setState((prev) => ({
          // 続きのときは後ろに足す。同じ人を2回出さない（検索で、読んでいる途中に並びがずれたときも）
          users: [...prev.users, ...chunk.users.filter((u) => !prev.users.some((p) => p.id === u.id))],
          next: nextOf(chunk),
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
      .then((chunk) => {
        if (!ignore) setState({ users: chunk.users, next: nextOf(chunk), status: 'ready' })
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
    if (!sentinel || state.next === null || state.status !== 'ready') return
    const next = state.next
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore(next)
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [sentinel, state.next, state.status, loadMore])

  // 失敗したときの「もう一度読み込む」
  const retry = useCallback(() => void loadMore(state.next ?? undefined), [loadMore, state.next])

  return { state, setSentinel, retry }
}
