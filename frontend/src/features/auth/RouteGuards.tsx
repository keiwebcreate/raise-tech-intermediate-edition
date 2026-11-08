import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth, type LoginPageState } from './AuthContext.ts'

// 画面の出し分け（authentication.md 2章・4.4）

// ログインしているかを確かめている間に出す、くるくる
function Checking() {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100dvh' }}>
      <CircularProgress aria-label="読み込み中" />
    </Box>
  )
}

// ログインが必要な画面。ログインしていなければ /login へ。ログインしたら、この画面へ戻す
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user === undefined) return <Checking />
  if (user === null) {
    const state: LoginPageState = { from: location.pathname + location.search }
    return <Navigate to="/login" replace state={state} />
  }
  return children
}

// ログインしていない人の画面（ログイン・新規登録）。ログインしていれば / へ
export function GuestOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user === undefined) return <Checking />
  if (user !== null) return <Navigate to="/" replace />
  return children
}
