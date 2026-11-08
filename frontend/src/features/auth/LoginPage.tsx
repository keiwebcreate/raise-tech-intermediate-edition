import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useLocation } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { AuthCard } from './AuthCard.tsx'
import { useAuth, type LoginPageState } from './AuthContext.ts'
import * as authApi from './authApi.ts'

type Errors = { email?: string; password?: string }

// ログインの後に戻る先。アプリの中の画面（/ から始まり、// ではない）だけにする
function safeNext(from: string | undefined): string {
  if (from && from.startsWith('/') && !from.startsWith('//') && from !== '/login' && from !== '/signup') {
    return from
  }
  return '/'
}

// S-01 ログイン（authentication.md 3.2・4.2）
export function LoginPage() {
  const { loggedIn } = useAuth()
  const state = useLocation().state as LoginPageState | null

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [alert, setAlert] = useState<string | null>(null)
  const [busy, setBusy] = useState(false) // 通信中はボタンを押せなくする

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault() // ブラウザの標準の送信（ページの再読み込み）を止める
    setAlert(null)

    // 画面のチェックは「空でないか」だけ。ルールはサーバーが確かめる
    const nextErrors: Errors = {}
    if (email.trim() === '') nextErrors.email = MESSAGES.required('メールアドレス')
    if (password === '') nextErrors.password = MESSAGES.required('パスワード')
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setBusy(true)
    try {
      loggedIn(await authApi.login({ email, password }), safeNext(state?.from))
    } catch (error) {
      setBusy(false)
      setPassword('') // 失敗したらパスワード欄を空にする
      setAlert(error instanceof ApiError ? error.detail : MESSAGES.network)
    }
  }

  return (
    <AuthCard onSubmit={handleSubmit}>
      <Typography component="h1" variant="h4" color="primary" sx={{ textAlign: 'center', fontWeight: 700 }}>
        RaiseTechtimeline
      </Typography>
      <Typography color="text.secondary" sx={{ textAlign: 'center', mt: -1 }}>
        学んだことを、短い言葉で残していこう。
      </Typography>
      {state?.notice && !alert && <Alert severity="info">{state.notice}</Alert>}
      {alert && (
        <Alert severity="error" role="alert">
          {alert}
        </Alert>
      )}
      <TextField
        id="login-email"
        label="メールアドレス"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={Boolean(errors.email)}
        helperText={errors.email}
        fullWidth
      />
      <TextField
        id="login-password"
        label="パスワード"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={Boolean(errors.password)}
        helperText={errors.password}
        fullWidth
      />
      <Button type="submit" variant="contained" size="large" disabled={busy} fullWidth>
        ログイン
      </Button>
      <Typography variant="body2" sx={{ textAlign: 'center' }}>
        アカウントをお持ちでない方は{' '}
        <Link component={RouterLink} to="/signup">
          新規登録
        </Link>
      </Typography>
    </AuthCard>
  )
}
