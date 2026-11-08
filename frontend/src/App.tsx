import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { GuestOnly, RequireAuth } from './features/auth/RouteGuards.tsx'
import { SignupPage } from './features/auth/SignupPage.tsx'
import { PostActionsProvider } from './features/post/PostActionsProvider.tsx'
import { PostDetailPage } from './features/post/PostDetailPage.tsx'
import { TimelinePage } from './features/timeline/TimelinePage.tsx'
import { AppLayout } from './layout/AppLayout.tsx'
import { NotificationProvider } from './shared/notification.tsx'
import { theme } from './theme.ts'

// ログインが必要な画面：ログインを確かめ、共通の並び（メニュー）で包む
function Member({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <PostActionsProvider>
        <AppLayout>{children}</AppLayout>
      </PostActionsProvider>
    </RequireAuth>
  )
}

// 画面の一覧（screen-design.md 2章）
export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <NotificationProvider>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route
                path="/login"
                element={
                  <GuestOnly>
                    <LoginPage />
                  </GuestOnly>
                }
              />
              <Route
                path="/signup"
                element={
                  <GuestOnly>
                    <SignupPage />
                  </GuestOnly>
                }
              />
              {/* S-03 タイムライン */}
              <Route
                path="/"
                element={
                  <Member>
                    <TimelinePage />
                  </Member>
                }
              />
              {/* S-04 投稿の詳細 */}
              <Route
                path="/posts/:id"
                element={
                  <Member>
                    <PostDetailPage />
                  </Member>
                }
              />
              {/* ない画面はホームへ（ログインしていなければ、さらに /login へ） */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </NotificationProvider>
    </ThemeProvider>
  )
}
