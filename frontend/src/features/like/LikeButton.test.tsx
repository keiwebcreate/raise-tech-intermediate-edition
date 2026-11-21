// いいねのボタン（like.md 3章・4.1・4.2・5章）
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MESSAGES } from '../../shared/messages.ts'
import { renderWithApp } from '../../test/render.tsx'
import { LikeButton } from './LikeButton.tsx'
import type { LikeState } from './likeToggler.ts'

function setup(props: { liked: boolean; likeCount: number; send?: (liked: boolean) => Promise<LikeState> }) {
  const send = vi.fn(props.send ?? (async (liked: boolean) => ({ liked, likeCount: props.likeCount + (liked ? 1 : -1) })))
  const onSettled = vi.fn()
  const onCountClick = vi.fn()
  const rendered = renderWithApp(
    <LikeButton
      liked={props.liked}
      likeCount={props.likeCount}
      send={send}
      onSettled={onSettled}
      onCountClick={onCountClick}
    />,
  )
  return { ...rendered, send, onSettled, onCountClick }
}

describe('LikeButton', () => {
  it('いいねしていなければ「いいねする」、いいね済みなら「いいねを取り消す」', () => {
    setup({ liked: false, likeCount: 2 })
    expect(screen.getByRole('button', { name: 'いいねする' })).toBeInTheDocument()

    setup({ liked: true, likeCount: 2 })
    expect(screen.getByRole('button', { name: 'いいねを取り消す' })).toBeInTheDocument()
  })

  it('数が 0 のときは数を出さない（4.2）', () => {
    setup({ liked: false, likeCount: 0 })

    expect(screen.queryByRole('button', { name: /いいねしたユーザー/ })).not.toBeInTheDocument()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })

  it('数を押すと、いいねしたユーザーの一覧を開く（onCountClick）', async () => {
    const { user, onCountClick } = setup({ liked: false, likeCount: 5 })

    await user.click(screen.getByRole('button', { name: 'いいねしたユーザー 5 人' }))

    expect(onCountClick).toHaveBeenCalledTimes(1)
  })

  it('押したらサーバーの返事を待たずに、ハートと数が変わる', async () => {
    let finish: (state: LikeState) => void = () => {}
    const { user, send } = setup({
      liked: false,
      likeCount: 5,
      send: () => new Promise<LikeState>((resolve) => (finish = resolve)),
    })

    await user.click(screen.getByRole('button', { name: 'いいねする' }))

    expect(send).toHaveBeenCalledWith(true)
    expect(screen.getByRole('button', { name: 'いいねを取り消す' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'いいねしたユーザー 6 人' })).toBeInTheDocument()
    finish({ liked: true, likeCount: 6 })
  })

  it('送り終えたら、サーバーの返事で表示をそろえて知らせる（onSettled）', async () => {
    const { user, onSettled } = setup({
      liked: false,
      likeCount: 5,
      // ほかの人も同時にいいねしていて、サーバーの数は 8
      send: async () => ({ liked: true, likeCount: 8 }),
    })

    await user.click(screen.getByRole('button', { name: 'いいねする' }))

    await waitFor(() => expect(onSettled).toHaveBeenCalledWith({ liked: true, likeCount: 8 }))
    expect(screen.getByRole('button', { name: 'いいねしたユーザー 8 人' })).toBeInTheDocument()
  })

  it('失敗したら元に戻し「いいねに失敗しました」', async () => {
    const { user, notify } = setup({
      liked: false,
      likeCount: 5,
      send: async () => {
        throw new Error('つながらない')
      },
    })

    await user.click(screen.getByRole('button', { name: 'いいねする' }))

    await waitFor(() => expect(notify).toHaveBeenCalledWith(MESSAGES.likeFailed))
    expect(screen.getByRole('button', { name: 'いいねする' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'いいねしたユーザー 5 人' })).toBeInTheDocument()
  })
})
