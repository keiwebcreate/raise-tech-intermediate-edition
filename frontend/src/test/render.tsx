// 部品をテストで描くときの包み。本物のアプリ（App.tsx）と同じように、テーマ・画面の切り替え（ルーター）・
// ログイン中の本人・お知らせ・投稿の操作で包む。中身はにせもので、呼ばれたかをテストで確かめられる
import { ThemeProvider } from '@mui/material/styles'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { vi } from 'vitest'
import type { UserResponse } from '../api/types.ts'
import { AuthContext } from '../features/auth/AuthContext.ts'
import { FollowProvider } from '../features/follow/FollowProvider.tsx'
import { PostActionsContext } from '../features/post/PostActionsContext.ts'
import { NotifyContext } from '../shared/notify.ts'
import { theme } from '../theme.ts'

// ログイン中の本人（太郎）
export const ME: UserResponse = {
  id: 1,
  email: 'taro@example.com',
  username: 'taro',
  displayName: '山田 太郎',
  iconUrl: null,
}

type Options = {
  // ログイン中の本人。undefined：確かめている途中、null：ログインしていない
  user?: UserResponse | null | undefined
  // 最初に開く URL（state も渡せる）
  url?: string | { pathname: string; search?: string; state?: unknown }
  // 本物の FollowProvider で包むか（フォローボタンのテスト）
  follow?: boolean
}

// 今の URL を画面に書き出す（画面が移ったかを確かめるため）
function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

export function renderWithApp(ui: ReactElement, options: Options = {}) {
  const auth = {
    user: 'user' in options ? options.user : ME,
    loggedIn: vi.fn(),
    logout: vi.fn(async () => {}),
    updateUser: vi.fn(),
  }
  const notify = vi.fn()
  const postActions = {
    openCreate: vi.fn(),
    openEdit: vi.fn(),
    requestDelete: vi.fn(),
    emit: vi.fn(),
    subscribe: vi.fn(() => () => {}),
  }

  const wrap = (children: ReactNode) => (
    <ThemeProvider theme={theme}>
      <NotifyContext value={notify}>
        <AuthContext value={auth}>
          <PostActionsContext value={postActions}>
            {options.follow ? <FollowProvider>{children}</FollowProvider> : children}
          </PostActionsContext>
        </AuthContext>
      </NotifyContext>
    </ThemeProvider>
  )

  const router = createMemoryRouter(
    [
      {
        path: '*',
        element: wrap(
          <>
            {ui}
            <LocationProbe />
          </>,
        ),
      },
    ],
    { initialEntries: [options.url ?? '/'] },
  )

  const user = userEvent.setup()
  const result = render(<RouterProvider router={router} />)
  return { ...result, user, auth, notify, postActions, router }
}
