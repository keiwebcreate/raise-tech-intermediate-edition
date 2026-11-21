// 投稿1件の表示（timeline.md 3.1、post.md 2章・4.4・4.5、like.md 4.1・4.2）
import { screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { post } from '../../test/data.ts'
import { renderWithApp } from '../../test/render.tsx'
import { setPostLike } from '../like/likeApi.ts'
import { PostCard } from './PostCard.tsx'

vi.mock('../like/likeApi.ts', () => ({ setPostLike: vi.fn(), getPostLikers: vi.fn() }))

const likeApi = vi.mocked(setPostLike)

beforeEach(() => {
  likeApi.mockReset()
})

describe('PostCard：表示', () => {
  it('表示名・@ユーザー名・本文を出し、名前を押すとその人のプロフィールへ（US-02）', () => {
    renderWithApp(<PostCard post={post(1)} />)

    expect(screen.getByText('投稿 1 の本文')).toBeInTheDocument()
    // 表示名と @ユーザー名は、まとめて1つのリンク
    expect(screen.getByRole('link', { name: '佐藤 花子 @hanako' })).toHaveAttribute('href', '/users/hanako')
  })

  it('本文の改行をそのまま出す', () => {
    renderWithApp(<PostCard post={post(1, { content: '1行目\n2行目' })} />)

    expect(screen.getByText(/1行目/)).toHaveStyle({ whiteSpace: 'pre-wrap' })
  })

  it('編集した投稿にだけ「編集済み」を出す', () => {
    renderWithApp(<PostCard post={post(1, { editedAt: '2026-11-21T11:00:00+09:00' })} />)
    expect(screen.getByText(/編集済み/)).toBeInTheDocument()
  })

  it('編集していなければ「編集済み」を出さない', () => {
    renderWithApp(<PostCard post={post(1)} />)
    expect(screen.queryByText(/編集済み/)).not.toBeInTheDocument()
  })

  it('コメント数を出し、押すと投稿の詳細へ', async () => {
    const { user } = renderWithApp(<PostCard post={post(1, { commentCount: 2 })} />)

    await user.click(screen.getByRole('link', { name: 'コメント 2 件' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/posts/1')
  })

  it('本文を押すと投稿の詳細を開く（3.1）', async () => {
    const { user } = renderWithApp(<PostCard post={post(1)} />)

    await user.click(screen.getByText('投稿 1 の本文'))

    expect(screen.getByTestId('location')).toHaveTextContent('/posts/1')
  })
})

describe('PostCard：「…」メニュー（自分の投稿だけ）', () => {
  it('他人の投稿には「…」を出さない（post.md 2章）', () => {
    renderWithApp(<PostCard post={post(1, { mine: false })} />)

    expect(screen.queryByRole('button', { name: '投稿のメニュー' })).not.toBeInTheDocument()
  })

  it('自分の投稿の「…」→「編集」で編集のモーダルを開く', async () => {
    const target = post(1, { mine: true })
    const { user, postActions } = renderWithApp(<PostCard post={target} />)

    await user.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    await user.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: '編集' }))

    expect(postActions.openEdit).toHaveBeenCalledWith(target)
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })

  it('自分の投稿の「…」→「削除」で削除の確認を出す', async () => {
    const target = post(1, { mine: true })
    const { user, postActions } = renderWithApp(<PostCard post={target} />)

    await user.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    await user.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: '削除' }))

    expect(postActions.requestDelete).toHaveBeenCalledWith(target)
  })
})

describe('PostCard：いいね', () => {
  it('ハートを押すとこの投稿にいいねを送り、ほかの画面にも知らせる（タイムラインと詳細をそろえる）', async () => {
    likeApi.mockResolvedValue({ liked: true, likeCount: 4 })
    const { user, postActions } = renderWithApp(<PostCard post={post(1, { likeCount: 3 })} />)

    await user.click(screen.getByRole('button', { name: 'いいねする' }))

    expect(likeApi).toHaveBeenCalledWith(1, true)
    await waitFor(() =>
      expect(postActions.emit).toHaveBeenCalledWith({ type: 'liked', id: 1, liked: true, likeCount: 4 }),
    )
    // ハートを押しても、投稿の詳細へは移らない
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})
