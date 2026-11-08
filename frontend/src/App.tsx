import CssBaseline from '@mui/material/CssBaseline'
import Typography from '@mui/material/Typography'

// 環境構築の確認用の仮の画面。ログイン・新規登録の画面は次のイシューで作る
export default function App() {
  return (
    <>
      <CssBaseline />
      <Typography component="h1" variant="h4" sx={{ p: 4 }}>
        RaiseTechtimeline
      </Typography>
    </>
  )
}
