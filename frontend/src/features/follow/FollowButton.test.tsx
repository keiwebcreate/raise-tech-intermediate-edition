// フォローボタン（profile.md 4.1〜4.3・7章、screen-design.md 3.6・M-03）
import { screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MESSAGES } from '../../shared/messages.ts'
import { renderWithApp } from '../../test/render.tsx'
import { setFollow } from './followApi.ts'
import { FollowButton } from './FollowButton.tsx'

vi.mock('./followApi.ts', () => ({ setFollow: vi.fn() }))

const followApi = vi.mocked(setFollow)
const HANAKO = { id: 2, username: 'hanako' }

// 外から返事を出せる Promise（送っている途中を確かめるため）
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

beforeEach(() => {
  followApi.mockReset()
  followApi.mockImplementation(async (_username, following) => ({ following, followerCount: 1 }))
})

describe('FollowButton：見た目', () => {
  it('フォローしていなければ「フォローする」', () => {
    renderWithApp(<FollowButton user={HANAKO} following={false} followedBy={false} />, { follow: true })

    expect(screen.getByRole('button', { name: 'フォローする @hanako' })).toBeInTheDocument()
  })

  it('相手だけが自分をフォローしていれば「フォローバック」（4.2）', () => {
    renderWithApp(<FollowButton user={HANAKO} following={false} followedBy />, { follow: true })

    expect(screen.getByRole('button', { name: 'フォローバック @hanako' })).toBeInTheDocument()
  })

  it('フォロー中なら「フォロー中」、マウスを乗せると「フォロー解除」', async () => {
    const { user } = renderWithApp(<FollowButton user={HANAKO} following followedBy={false} />, { follow: true })

    const button = screen.getByRole('button', { name: 'フォロー中 @hanako' })
    await user.hover(button)

    expect(screen.getByRole('button', { name: 'フォロー解除 @hanako' })).toBeInTheDocument()
  })

  it('自分にはボタンを出さない', () => {
    renderWithApp(<FollowButton user={{ id: 1, username: 'taro' }} following={false} followedBy={false} />, {
      follow: true,
    })

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('FollowButton：フォロー', () => {
  it('押したらすぐ「フォロー中」になり、サーバーへフォローを送る', async () => {
    const pending = deferred<{ following: boolean; followerCount: number }>()
    followApi.mockReturnValueOnce(pending.promise)
    const { user } = renderWithApp(<FollowButton user={HANAKO} following={false} followedBy={false} />, {
      follow: true,
    })

    await user.click(screen.getByRole('button', { name: 'フォローする @hanako' }))

    expect(followApi).toHaveBeenCalledWith('hanako', true)
    // 返事を待たずに変わる。マウスが乗ったままでも、すぐ「フォロー解除」にはしない（7章）
    expect(screen.getByRole('button', { name: /^フォロー中/ })).toBeInTheDocument()
    pending.resolve({ following: true, followerCount: 1 })
  })

  it('送っている間は押せない（7章）', async () => {
    const pending = deferred<{ following: boolean; followerCount: number }>()
    followApi.mockReturnValueOnce(pending.promise)
    const { user } = renderWithApp(<FollowButton user={HANAKO} following={false} followedBy={false} />, {
      follow: true,
    })

    await user.click(screen.getByRole('button', { name: 'フォローする @hanako' }))

    expect(screen.getByRole('button', { name: /@hanako/ })).toBeDisabled()
    pending.resolve({ following: true, followerCount: 1 })
    await waitFor(() => expect(screen.getByRole('button', { name: /@hanako/ })).toBeEnabled())
  })

  it('失敗したら元に戻し「フォローに失敗しました」', async () => {
    followApi.mockRejectedValueOnce(new Error('つながらない'))
    const { user, notify } = renderWithApp(<FollowButton user={HANAKO} following={false} followedBy={false} />, {
      follow: true,
    })

    await user.click(screen.getByRole('button', { name: 'フォローする @hanako' }))

    await waitFor(() => expect(notify).toHaveBeenCalledWith(MESSAGES.followFailed))
    expect(screen.getByRole('button', { name: 'フォローする @hanako' })).toBeInTheDocument()
  })

  it('画面の中の同じ人のボタンもそろう（7章）', async () => {
    const { user } = renderWithApp(
      <>
        <FollowButton user={HANAKO} following={false} followedBy={false} />
        <FollowButton user={HANAKO} following={false} followedBy={false} />
      </>,
      { follow: true },
    )

    await user.click(screen.getAllByRole('button', { name: 'フォローする @hanako' })[0])

    await waitFor(() => expect(screen.getAllByRole('button', { name: /^フォロー中/ })).toHaveLength(2))
  })
})

describe('FollowButton：解除（M-03）', () => {
  it('押すと「@hanako さんのフォローを解除しますか？」と聞き、キャンセルなら何も送らない', async () => {
    const { user } = renderWithApp(<FollowButton user={HANAKO} following followedBy={false} />, { follow: true })

    await user.click(screen.getByRole('button', { name: /@hanako/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('@hanako さんのフォローを解除しますか？')

    await user.click(within(dialog).getByRole('button', { name: 'キャンセル' }))

    expect(followApi).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: /^フォロー(中|解除) @hanako/ })).toBeInTheDocument()
  })

  it('「フォロー解除」を押すと解除を送り、「フォローする」に変わる', async () => {
    const { user } = renderWithApp(<FollowButton user={HANAKO} following followedBy={false} />, { follow: true })

    await user.click(screen.getByRole('button', { name: /@hanako/ }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'フォロー解除' }))

    expect(followApi).toHaveBeenCalledWith('hanako', false)
    await waitFor(() => expect(screen.getByRole('button', { name: 'フォローする @hanako' })).toBeInTheDocument())
  })
})
