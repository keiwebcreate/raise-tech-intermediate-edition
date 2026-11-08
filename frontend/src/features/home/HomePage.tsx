import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext.ts'

// 仮のホーム画面（講義の指示：ログインできたことが分かればよい）。
// タイムライン（S-03）を作るときに置きかえる
export function HomePage() {
  const { user, logout } = useAuth()
  const [busy, setBusy] = useState(false)

  async function handleLogout() {
    setBusy(true)
    await logout()
  }

  return (
    <Box sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', px: 2 }}>
      <Paper variant="outlined" sx={{ width: '100%', maxWidth: 420, p: 4, textAlign: 'center' }}>
        <Typography component="h1" variant="h4" color="primary" sx={{ fontWeight: 700 }}>
          ログイン成功
        </Typography>
        {user && (
          <Typography sx={{ mt: 2, overflowWrap: 'anywhere' }}>
            ようこそ、{user.displayName} さん（@{user.username}）
          </Typography>
        )}
        <Button variant="outlined" onClick={handleLogout} disabled={busy} sx={{ mt: 3 }}>
          ログアウト
        </Button>
      </Paper>
    </Box>
  )
}
