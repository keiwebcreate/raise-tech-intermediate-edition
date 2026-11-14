import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { ConfirmDialog } from '../../shared/ConfirmDialog.tsx'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import * as followApi from './followApi.ts'
import { FollowContext, type FollowTarget } from './FollowContext.ts'
import type { FollowEvent } from './followState.ts'

// フォロー・解除（profile.md 4.1）と、解除の確認（M-03）を持つ。
// - 押したらすぐ表示を変え（overrides）、失敗したら元に戻して「フォローに失敗しました」
// - 送っている間は、その人のボタンを押せなくする（解除には確認があるので、連打はまず起きない）
// - 済んだら FollowEvent で知らせる（プロフィールのフォロー数・フォロワー数をそろえる）
export function FollowProvider({ children }: { children: ReactNode }) {
  const notify = useNotify()
  const [overrides, setOverrides] = useState<ReadonlyMap<number, boolean>>(new Map())
  const [pending, setPending] = useState<ReadonlySet<number>>(new Set())
  const [confirming, setConfirming] = useState<FollowTarget | null>(null)
  const listeners = useRef(new Set<(event: FollowEvent) => void>())

  const setOverride = useCallback((id: number, value: boolean) => {
    setOverrides((prev) => new Map(prev).set(id, value))
  }, [])

  const setPendingOf = useCallback((id: number, value: boolean) => {
    setPending((prev) => {
      const next = new Set(prev)
      if (value) next.add(id)
      else next.delete(id)
      return next
    })
  }, [])

  const send = useCallback(
    async (target: FollowTarget, following: boolean) => {
      const previous = !following
      setOverride(target.id, following)
      setPendingOf(target.id, true)
      try {
        const result = await followApi.setFollow(target.username, following)
        setOverride(target.id, result.following)
        const event: FollowEvent = {
          userId: target.id,
          following: result.following,
          previous,
          followerCount: result.followerCount,
        }
        listeners.current.forEach((listener) => listener(event))
      } catch {
        setOverride(target.id, previous)
        notify(MESSAGES.followFailed)
      } finally {
        setPendingOf(target.id, false)
      }
    },
    [notify, setOverride, setPendingOf],
  )

  const toggle = useCallback(
    (target: FollowTarget, current: boolean) => {
      // 解除は押し間違いを防ぐため、確かめてから（4.1）
      if (current) setConfirming(target)
      else void send(target, true)
    },
    [send],
  )

  const subscribe = useCallback((listener: (event: FollowEvent) => void) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  const value = useMemo(() => ({ overrides, pending, toggle, subscribe }), [overrides, pending, toggle, subscribe])

  return (
    <FollowContext value={value}>
      {children}
      <ConfirmDialog
        open={confirming !== null}
        title={`@${confirming?.username ?? ''} さんのフォローを解除しますか？`}
        confirmLabel="フォロー解除"
        onConfirm={() => {
          if (confirming) void send(confirming, false)
          setConfirming(null)
        }}
        onCancel={() => setConfirming(null)}
      />
    </FollowContext>
  )
}
