import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { GuestOnly, RequireAuth } from './features/auth/RouteGuards.tsx'
import { SignupPage } from './features/auth/SignupPage.tsx'
import { HomePage } from './features/home/HomePage.tsx'
import { theme } from './theme.ts'

// 画面の一覧（screen-design.md）。今はログイン・新規登録と仮のホーム画面だけ
export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
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
            <Route
              path="/"
              element={
                <RequireAuth>
                  <HomePage />
                </RequireAuth>
              }
            />
            {/* ない画面はホームへ（ログインしていなければ、さらに /login へ） */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}
