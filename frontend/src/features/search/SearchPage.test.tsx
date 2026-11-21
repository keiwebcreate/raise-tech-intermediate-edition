// ユーザー検索の画面（S-08。user-search.md 3章・4.1・5章、screen-design.md 3.6）
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MESSAGES } from '../../shared/messages.ts'
import { listedUser } from '../../test/data.ts'
import { renderWithApp } from '../../test/render.tsx'
import { searchUsers } from './searchApi.ts'
import { SearchPage } from './SearchPage.tsx'

vi.mock('./searchApi.ts', () => ({ searchUsers: vi.fn() }))

const searchApi = vi.mocked(searchUsers)

function setup(url = '/search') {
  return renderWithApp(<SearchPage />, { url, follow: true })
}

// 検索欄は type="search" なので、役割は searchbox
const searchBox = () => screen.getByRole('searchbox', { name: 'ユーザーを検索' })

beforeEach(() => {
  searchApi.mockReset()
  searchApi.mockResolvedValue({
    users: [
      listedUser(1, { username: 'taro', displayName: '山田 太郎' }),
      listedUser(5, { username: 'taro_dev', displayName: 'たろう', followedBy: true }),
    ],
    nextOffset: null,
  })
})

describe('SearchPage：入力する前', () => {
  it('「ユーザー名や名前で検索できます」と出し、検索欄にすぐ打てる。まだ検索しない', () => {
    setup()

    expect(screen.getByText('ユーザー名や名前で検索できます')).toBeInTheDocument()
    expect(searchBox()).toHaveFocus()
    expect(searchApi).not.toHaveBeenCalled()
  })
})

describe('SearchPage：URL の言葉で検索する（戻る・再読み込みでも同じ結果）', () => {
  it('?q= の言葉で検索し、結果を並べる。自分にはフォローボタンを出さない', async () => {
    setup('/search?q=taro')

    const list = await screen.findByRole('list', { name: '検索結果' })
    expect(searchApi.mock.calls[0][0]).toBe('taro')
    expect(searchBox()).toHaveValue('taro')
    expect(within(list).getByText('山田 太郎')).toBeInTheDocument()
    expect(within(list).getByText('たろう')).toBeInTheDocument()
    expect(within(list).queryByRole('button', { name: /@taro$/ })).not.toBeInTheDocument()
    expect(within(list).getByRole('button', { name: 'フォローバック @taro_dev' })).toBeInTheDocument()
  })

  it('見つからなければ「「○○」に一致するユーザーは見つかりませんでした」', async () => {
    searchApi.mockResolvedValue({ users: [], nextOffset: null })
    setup('/search?q=nobody')

    expect(await screen.findByText('「nobody」に一致するユーザーは見つかりませんでした')).toBeInTheDocument()
  })

  it('失敗したら「検索に失敗しました」と「もう一度試す」。押すと検索し直す', async () => {
    searchApi.mockRejectedValueOnce(new Error('つながらない'))
    const { user } = setup('/search?q=taro')

    expect(await screen.findByText(MESSAGES.searchFailed)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'もう一度試す' }))

    expect(await screen.findByText('山田 太郎')).toBeInTheDocument()
    expect(searchApi).toHaveBeenCalledTimes(2)
  })
})

describe('SearchPage：打って検索する', () => {
  it('打つのが止まったら検索し、言葉を URL に入れる', async () => {
    const { user } = setup()

    await user.type(searchBox(), 'taro')

    await waitFor(() => expect(searchApi).toHaveBeenCalled())
    expect(searchApi.mock.calls.at(-1)?.[0]).toBe('taro')
    expect(screen.getByTestId('location')).toHaveTextContent('/search?q=taro')
    expect(await screen.findByText('山田 太郎')).toBeInTheDocument()
  })

  it('1文字ごとには検索しない（止まってから1回）', async () => {
    const { user } = setup()

    await user.type(searchBox(), 'taro')

    await waitFor(() => expect(searchApi).toHaveBeenCalled())
    expect(searchApi).toHaveBeenCalledTimes(1)
  })

  it('日本語の変換中は検索せず、変換が終わってから検索する（4.1）', async () => {
    setup()
    const box = searchBox()

    fireEvent.compositionStart(box)
    fireEvent.change(box, { target: { value: 'たろ' } })
    await new Promise((resolve) => setTimeout(resolve, 500))
    expect(searchApi).not.toHaveBeenCalled()

    fireEvent.change(box, { target: { value: '太郎' } })
    fireEvent.compositionEnd(box)

    await waitFor(() => expect(searchApi).toHaveBeenCalled())
    expect(searchApi.mock.calls.at(-1)?.[0]).toBe('太郎')
  })

  it('先頭の @ は無視して探す（3章）', async () => {
    const { user } = setup()

    await user.type(searchBox(), '@taro')

    await waitFor(() => expect(searchApi).toHaveBeenCalled())
    expect(searchApi.mock.calls.at(-1)?.[0]).toBe('taro')
  })

  it('51文字以上は欄の下に「検索する言葉は50文字以内で入力してください」と出し、検索しない', async () => {
    const { user } = setup()

    await user.click(searchBox())
    await user.paste('あ'.repeat(51))

    expect(await screen.findByText('検索する言葉は50文字以内で入力してください')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 500))
    expect(searchApi).not.toHaveBeenCalled()
  })
})
