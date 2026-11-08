import { useCallback, useEffect, useState } from 'react'
import { getNewPostCount, type Tab } from '../post/postApi.ts'

// 新しい投稿を確かめる間隔（timeline.md 3.2）
export const NEW_POST_POLL_MS = 60_000

// 一覧の一番上の投稿（topId）より新しい、自分以外の投稿の数を、60秒ごとに確かめる。
// ブラウザのタブが隠れている間は確かめず、見えるようになったらすぐ1回確かめる。
// WebSocket は使わない（読んでいる途中で一覧が勝手に動かないよう、押したときだけ足すため。timeline.md 3.2）
export function useNewPostCount(tab: Tab, topId: number | undefined, enabled: boolean) {
  const [count, setCount] = useState(0)

  useEffect(() => {
    if (!enabled) return
    let cancelled = false

    function check() {
      if (document.visibilityState !== 'visible') return
      getNewPostCount(tab, topId)
        .then((n) => {
          if (!cancelled) setCount(n)
        })
        .catch(() => {
          // 確かめられなくても、次の60秒でもう一度試す（画面にエラーは出さない）
        })
    }

    const timer = window.setInterval(check, NEW_POST_POLL_MS)
    document.addEventListener('visibilitychange', check)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', check)
    }
  }, [tab, topId, enabled])

  // タブ・一番上の投稿が変わったら、数え直す
  const [prevKey, setPrevKey] = useState(`${tab}:${topId}`)
  if (prevKey !== `${tab}:${topId}`) {
    setPrevKey(`${tab}:${topId}`)
    setCount(0)
  }

  const reset = useCallback(() => setCount(0), [])
  return { count, reset }
}
