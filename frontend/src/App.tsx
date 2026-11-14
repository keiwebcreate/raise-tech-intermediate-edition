import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import type { ReactNode } from 'react'
import { createBrowserRouter, Navigate, Route, RouterProvider, Routes } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { GuestOnly, RequireAuth } from './features/auth/RouteGuards.tsx'
import { SignupPage } from './features/auth/SignupPage.tsx'
import { FollowListPage } from './features/follow/FollowListPage.tsx'
import { FollowProvider } from './features/follow/FollowProvider.tsx'
import { PostActionsProvider } from './features/post/PostActionsProvider.tsx'
import { PostDetailPage } from './features/post/PostDetailPage.tsx'
import { ProfileEditPage } from './features/profile/ProfileEditPage.tsx'
import { ProfilePage } from './features/profile/ProfilePage.tsx'
import { TimelinePage } from './features/timeline/TimelinePage.tsx'
import { AppLayout } from './layout/AppLayout.tsx'
import { NotificationProvider } from './shared/notification.tsx'
import { theme } from './theme.ts'

// ログインが必要な画面：ログインを確かめ、共通の並び（メニュー）で包む
function Member({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <PostActionsProvider>
        <FollowProvider>
          <AppLayout>{children}</AppLayout>
        </FollowProvider>
      </PostActionsProvider>
    </RequireAuth>
  )
}

// 画面の一覧（screen-design.md 2章）
function AppRoutes() {
  return (
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
        {/* S-05 プロフィール */}
        <Route
          path="/users/:username"
          element={
            <Member>
              <ProfilePage />
            </Member>
          }
        />
        {/* S-07 フォロー中の一覧 */}
        <Route
          path="/users/:username/following"
          element={
            <Member>
              <FollowListPage kind="following" />
            </Member>
          }
        />
        {/* S-07 フォロワーの一覧 */}
        <Route
          path="/users/:username/followers"
          element={
            <Member>
              <FollowListPage kind="followers" />
            </Member>
          }
        />
        {/* S-06 プロフィールの編集 */}
        <Route
          path="/settings/profile"
          element={
            <Member>
              <ProfileEditPage />
            </Member>
          }
        />
        {/* ない画面はホームへ（ログインしていなければ、さらに /login へ） */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

// 画面の切り替えは createBrowserRouter で作る（useBlocker：保存せずに離れるときの確認に要る。profile.md 3.2）。
// 画面の一覧は今までどおり AppRoutes の <Routes> に書き、すべての URL をそこへ渡す
const router = createBrowserRouter([{ path: '*', element: <AppRoutes /> }])

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <NotificationProvider>
        <RouterProvider router={router} />
      </NotificationProvider>
    </ThemeProvider>
  )
}
