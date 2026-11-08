import Snackbar from '@mui/material/Snackbar'
import { useCallback, useState, type ReactNode } from 'react'
import { NotifyContext } from './notify.ts'

// 画面の下に少しの間だけ出すお知らせ（screen-design.md 3.4。「投稿しました」など）。出すときは useNotify() を使う
export function NotificationProvider({ children }: { children: ReactNode }) {
  // key：同じ文言が続けて出たときも、出し直す（Snackbar を作り直す）ため
  const [notice, setNotice] = useState<{ key: number; message: string } | null>(null)

  const notify = useCallback((message: string) => {
    setNotice({ key: Date.now(), message })
  }, [])

  return (
    <NotifyContext value={notify}>
      {children}
      <Snackbar
        key={notice?.key}
        open={notice !== null}
        message={notice?.message}
        autoHideDuration={3000}
        onClose={(_, reason) => {
          if (reason !== 'clickaway') setNotice(null)
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        // スマホでは下のメニューに重ならないように、少し上に出す
        sx={{ bottom: { xs: 80, sm: 24 } }}
      />
    </NotifyContext>
  )
}
