// 新規登録画面（S-02。authentication.md 3.1・4.1・5章）
import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { ME, renderWithApp } from '../../test/render.tsx'
import { signup } from './authApi.ts'
import { SignupPage } from './SignupPage.tsx'

vi.mock('./authApi.ts', () => ({ signup: vi.fn() }))

const signupApi = vi.mocked(signup)

function setup() {
  return renderWithApp(<SignupPage />, { user: null, url: '/signup' })
}

// 「パスワード」は「パスワード（確認）」と分けて探す
const box = {
  email: () => screen.getByLabelText(/^メールアドレス/),
  username: () => screen.getByLabelText(/^ユーザー名/),
  displayName: () => screen.getByLabelText(/^表示名/),
  password: () => screen.getByLabelText(/^パスワード(?!（)/),
  confirm: () => screen.getByLabelText(/^パスワード（確認）/),
}
const signupButton = () => screen.getByRole('button', { name: '登録する' })

type Setup = ReturnType<typeof setup>

async function fill(user: Setup['user'], change: Partial<Record<keyof typeof box, string>> = {}) {
  const values = {
    email: 'taro@example.com',
    username: 'taro_1',
    displayName: '山田 太郎',
    password: 'password123',
    confirm: 'password123',
    ...change,
  }
  for (const [name, value] of Object.entries(values) as [keyof typeof box, string][]) {
    if (value) await user.type(box[name](), value)
  }
}

beforeEach(() => {
  signupApi.mockReset()
  signupApi.mockResolvedValue(ME)
})

describe('SignupPage', () => {
  it('入力を送り（確認のパスワードは送らない）、登録できたらログインした状態でタイムラインへ', async () => {
    const { user, auth } = setup()

    await fill(user)
    await user.click(signupButton())

    expect(signupApi).toHaveBeenCalledWith({
      email: 'taro@example.com',
      username: 'taro_1',
      displayName: '山田 太郎',
      password: 'password123',
    })
    await waitFor(() => expect(auth.loggedIn).toHaveBeenCalled())
    expect(auth.loggedIn.mock.calls[0][0]).toEqual(ME)
  })

  it('確認のパスワードが違えば「パスワードが一致しません」と出し、送らない（画面だけで確かめる）', async () => {
    const { user } = setup()

    await fill(user, { confirm: 'password999' })
    await user.click(signupButton())

    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
    expect(signupApi).not.toHaveBeenCalled()
  })

  it('空のまま押すと「○○を入力してください」と出し、送らない', async () => {
    const { user } = setup()

    await user.click(signupButton())

    expect(screen.getByText(MESSAGES.required('メールアドレス'))).toBeInTheDocument()
    expect(screen.getByText('表示名を入力してください')).toBeInTheDocument()
    expect(signupApi).not.toHaveBeenCalled()
  })

  it('ユーザー名の形が違えば、決まりの文言を出す', async () => {
    const { user } = setup()

    await fill(user, { username: 'ta' })
    await user.click(signupButton())

    expect(screen.getByText('ユーザー名は3〜15文字の半角英数字と _ で入力してください')).toBeInTheDocument()
    expect(signupApi).not.toHaveBeenCalled()
  })

  it('サーバーが「登録済み」と返したら、その文言を出し、入力は残す', async () => {
    const message = 'このメールアドレスは登録済みです'
    signupApi.mockRejectedValueOnce(
      new ApiError(409, 'EMAIL_DUPLICATED', message, [{ field: 'email', message }]),
    )
    const { user, auth } = setup()

    await fill(user)
    await user.click(signupButton())

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(auth.loggedIn).not.toHaveBeenCalled()
    expect(box.email()).toHaveValue('taro@example.com')
  })

  it('つながらなかったら「通信に失敗しました」の文言を出す', async () => {
    signupApi.mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', MESSAGES.network))
    const { user } = setup()

    await fill(user)
    await user.click(signupButton())

    expect(await screen.findByText(MESSAGES.network)).toBeInTheDocument()
  })

  it('ログインの画面へのリンクがある', () => {
    setup()

    expect(screen.getByRole('link', { name: /ログイン/ })).toHaveAttribute('href', '/login')
  })
})
