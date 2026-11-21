// ログイン画面（S-01。authentication.md 3.2・4.2・4.4・5章）
import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { ME, renderWithApp } from '../../test/render.tsx'
import { login } from './authApi.ts'
import { LoginPage } from './LoginPage.tsx'

vi.mock('./authApi.ts', () => ({ login: vi.fn() }))

const loginApi = vi.mocked(login)

// ログイン画面は、ログインしていない人が開く
function setup(state?: unknown) {
  return renderWithApp(<LoginPage />, { user: null, url: { pathname: '/login', state } })
}

const emailBox = () => screen.getByLabelText(/^メールアドレス/)
const passwordBox = () => screen.getByLabelText(/^パスワード/)
const loginButton = () => screen.getByRole('button', { name: 'ログイン' })

beforeEach(() => {
  loginApi.mockReset()
  loginApi.mockResolvedValue(ME)
})

describe('LoginPage', () => {
  it('空のまま押すと、項目ごとに「○○を入力してください」と出し、送らない', async () => {
    const { user } = setup()

    await user.click(loginButton())

    expect(screen.getByText(MESSAGES.required('メールアドレス'))).toBeInTheDocument()
    expect(screen.getByText(MESSAGES.required('パスワード'))).toBeInTheDocument()
    expect(loginApi).not.toHaveBeenCalled()
  })

  it('メールアドレスとパスワードを送り、ログインできたらタイムラインへ（loggedIn）', async () => {
    const { user, auth } = setup()

    await user.type(emailBox(), 'taro@example.com')
    await user.type(passwordBox(), 'password123')
    await user.click(loginButton())

    expect(loginApi).toHaveBeenCalledWith({ email: 'taro@example.com', password: 'password123' })
    await waitFor(() => expect(auth.loggedIn).toHaveBeenCalled())
    expect(auth.loggedIn.mock.calls[0][0]).toEqual(ME)
    // 行き先は「なければ /」（AuthContext の loggedIn）。渡さないか '/' のどちらでもタイムラインへ移る
    expect([undefined, '/']).toContain(auth.loggedIn.mock.calls[0][1])
  })

  it('開こうとしていた画面があれば、ログインのあとそこへ戻す（4.2）', async () => {
    const { user, auth } = setup({ from: '/posts/3' })

    await user.type(emailBox(), 'taro@example.com')
    await user.type(passwordBox(), 'password123')
    await user.click(loginButton())

    await waitFor(() => expect(auth.loggedIn).toHaveBeenCalledWith(ME, '/posts/3'))
  })

  it.each([
    ['ほかのサイト（//から始まる）', '//evil.example/'],
    ['住所の全体', 'https://evil.example/'],
    ['ログイン画面そのもの', '/login'],
    ['新規登録の画面', '/signup'],
  ])('戻り先が %s なら使わず、タイムラインへ', async (_label, from) => {
    const { user, auth } = setup({ from })

    await user.type(emailBox(), 'taro@example.com')
    await user.type(passwordBox(), 'password123')
    await user.click(loginButton())

    await waitFor(() => expect(auth.loggedIn).toHaveBeenCalled())
    expect([undefined, '/']).toContain(auth.loggedIn.mock.calls[0][1])
  })

  it('間違えたら「メールアドレスまたはパスワードが正しくありません」と出し、入力は残す', async () => {
    const detail = 'メールアドレスまたはパスワードが正しくありません'
    loginApi.mockRejectedValueOnce(new ApiError(401, 'LOGIN_FAILED', detail))
    const { user, auth } = setup()

    await user.type(emailBox(), 'taro@example.com')
    await user.type(passwordBox(), 'wrong-password')
    await user.click(loginButton())

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(auth.loggedIn).not.toHaveBeenCalled()
    expect(emailBox()).toHaveValue('taro@example.com')
  })

  it('失敗が多すぎるときは「しばらくしてから再度お試しください」', async () => {
    const detail = 'しばらくしてから再度お試しください'
    loginApi.mockRejectedValueOnce(new ApiError(429, 'RATE_LIMITED', detail))
    const { user } = setup()

    await user.type(emailBox(), 'taro@example.com')
    await user.type(passwordBox(), 'password123')
    await user.click(loginButton())

    expect(await screen.findByText(detail)).toBeInTheDocument()
  })

  it('ログインの期限が切れて移ってきたときは、そのお知らせを出す（4.4）', () => {
    setup({ notice: MESSAGES.sessionExpired })

    expect(screen.getByText(MESSAGES.sessionExpired)).toBeInTheDocument()
  })

  it('新規登録の画面へのリンクがある', () => {
    setup()

    expect(screen.getByRole('link', { name: /新規登録/ })).toHaveAttribute('href', '/signup')
  })
})
