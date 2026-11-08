import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import type { FormEvent, ReactNode } from 'react'

type Props = {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  children: ReactNode
}

// ログイン・新規登録の画面の枠（モックアップの .auth-card）。画面のまん中に白いカードを置く
export function AuthCard({ onSubmit, children }: Props) {
  return (
    <Box sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', px: 2, py: 4 }}>
      <Paper
        component="form"
        onSubmit={onSubmit}
        noValidate
        variant="outlined"
        sx={{
          width: '100%',
          maxWidth: 420,
          p: { xs: 3, sm: 4 },
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {children}
      </Paper>
    </Box>
  )
}
