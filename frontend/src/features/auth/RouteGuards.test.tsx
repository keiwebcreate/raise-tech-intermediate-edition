// ログインしているかで画面を分ける（authentication.md 2章・4.2・4.4）
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { renderWithApp } from '../../test/render.tsx'
import { GuestOnly, RequireAuth } from './RouteGuards.tsx'

// 本物のアプリ（App.tsx）と同じように、ログイン・新規登録とそれ以外の画面を分けて置く
const routes = (
  <Routes>
    <Route
      path="/login"
      element={
        <GuestOnly>
          <p>ログイン画面</p>
        </GuestOnly>
      }
    />
    <Route
      path="*"
      element={
        <RequireAuth>
          <p>ログイン中の画面</p>
        </RequireAuth>
      }
    />
  </Routes>
)

describe('RequireAuth（ログインが必要な画面）', () => {
  it('ログインしていれば、中身を出す', () => {
    renderWithApp(routes, { url: '/posts/3' })

    expect(screen.getByText('ログイン中の画面')).toBeInTheDocument()
  })

  it('確かめている途中は「読み込み中」を出し、中身はまだ出さない', () => {
    renderWithApp(routes, { user: undefined, url: '/posts/3' })

    expect(screen.getByRole('progressbar', { name: '読み込み中' })).toBeInTheDocument()
    expect(screen.queryByText('ログイン中の画面')).not.toBeInTheDocument()
  })

  it('ログインしていなければログイン画面へ移し、開こうとしていた画面を覚えておく（4.2）', () => {
    const { router } = renderWithApp(routes, { user: null, url: '/posts/3?x=1' })

    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/login$/)
    expect(router.state.location.state).toMatchObject({ from: '/posts/3?x=1' })
  })
})

describe('GuestOnly（新規登録・ログインの画面）', () => {
  it('ログインしていなければ、中身を出す', () => {
    renderWithApp(routes, { user: null, url: '/login' })

    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
  })

  it('ログインしている人が開いたら、タイムラインへ移す（2章）', () => {
    renderWithApp(routes, { url: '/login' })

    expect(screen.getByText('ログイン中の画面')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})
