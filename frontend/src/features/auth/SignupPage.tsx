import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import InputAdornment from '@mui/material/InputAdornment'
import Link from '@mui/material/Link'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { AuthCard } from './AuthCard.tsx'
import { useAuth } from './AuthContext.ts'
import * as authApi from './authApi.ts'
import { validateSignup, type SignupErrors, type SignupForm } from './validation.ts'

const EMPTY_FORM: SignupForm = { email: '', username: '', displayName: '', password: '', passwordConfirm: '' }

// サーバーの code と、エラーを出す項目の対応（authentication.md 5章）
const DUPLICATED_FIELDS: Record<string, keyof SignupForm> = {
  EMAIL_DUPLICATED: 'email',
  USERNAME_DUPLICATED: 'username',
}

// S-02 新規登録（authentication.md 3.1・4.1）。登録できたら、そのままログインした状態になる
export function SignupPage() {
  const { loggedIn } = useAuth()
  const [form, setForm] = useState<SignupForm>(EMPTY_FORM)
  const [errors, setErrors] = useState<SignupErrors>({})
  const [alert, setAlert] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 入力欄の共通の設定（値・変更・エラー）
  function fieldProps(name: keyof SignupForm) {
    return {
      id: `signup-${name}`,
      name,
      value: form[name],
      onChange: (e: { target: { value: string } }) => setForm((prev) => ({ ...prev, [name]: e.target.value })),
      error: Boolean(errors[name]),
      fullWidth: true,
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAlert(null)
    const nextErrors = validateSignup(form)
    setErrors(nextErrors)
    const first = Object.keys(nextErrors)[0]
    if (first) {
      document.getElementById(`signup-${first}`)?.focus() // 最初の誤りの欄にカーソルを置く
      return
    }

    setBusy(true)
    try {
      const user = await authApi.signup({
        email: form.email.trim(),
        username: form.username,
        displayName: form.displayName.trim(),
        password: form.password,
      })
      // 登録後はタイムラインの「すべて」タブへ（authentication.md 4.1）。今は仮のホーム画面
      loggedIn(user)
    } catch (error) {
      setBusy(false)
      if (!(error instanceof ApiError)) {
        setAlert(MESSAGES.network)
        return
      }
      const duplicatedField = DUPLICATED_FIELDS[error.code]
      if (duplicatedField) {
        setErrors({ [duplicatedField]: error.detail })
      } else if (error.errors.length > 0) {
        setErrors(fieldErrorsOf(error) as SignupErrors)
      } else {
        setAlert(error.detail)
      }
    }
  }

  return (
    <AuthCard onSubmit={handleSubmit}>
      <Typography component="h1" variant="h5" sx={{ textAlign: 'center', fontWeight: 700 }}>
        アカウントを作成
      </Typography>
      {alert && (
        <Alert severity="error" role="alert">
          {alert}
        </Alert>
      )}
      <TextField
        {...fieldProps('email')}
        label="メールアドレス"
        type="email"
        autoComplete="email"
        helperText={errors.email}
      />
      <TextField
        {...fieldProps('username')}
        label="ユーザー名"
        autoComplete="username"
        helperText={errors.username ?? '3〜15文字の半角英数字と _。あとから変えられません'}
        slotProps={{ input: { startAdornment: <InputAdornment position="start">@</InputAdornment> } }}
      />
      <TextField
        {...fieldProps('displayName')}
        label="表示名"
        autoComplete="nickname"
        helperText={errors.displayName ?? '50文字まで'}
      />
      <TextField
        {...fieldProps('password')}
        label="パスワード"
        type="password"
        autoComplete="new-password"
        helperText={errors.password ?? '8〜64文字の半角英数字・記号'}
      />
      <TextField
        {...fieldProps('passwordConfirm')}
        label="パスワード（確認）"
        type="password"
        autoComplete="new-password"
        helperText={errors.passwordConfirm}
      />
      <Button type="submit" variant="contained" size="large" disabled={busy} fullWidth>
        登録する
      </Button>
      <Typography variant="body2" sx={{ textAlign: 'center' }}>
        アカウントをお持ちの方は{' '}
        <Link component={RouterLink} to="/login">
          ログイン
        </Link>
      </Typography>
    </AuthCard>
  )
}
