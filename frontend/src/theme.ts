import { createTheme } from '@mui/material/styles'

// 色はモックアップ（mockup/style.css の :root）から移した。紺のインクと青白い紙
export const theme = createTheme({
  palette: {
    primary: { main: '#2f45d1', dark: '#2234a8' }, // ボタン・リンク
    error: { main: '#c8233a' },
    warning: { main: '#a86400' },
    text: { primary: '#1c2452', secondary: '#5a6386' }, // 文字・補足の文字
    background: { default: '#f4f6fb', paper: '#ffffff' }, // 背景・本文の面
    divider: '#dce1ee',
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: "'Hiragino Kaku Gothic ProN', 'Yu Gothic', Meiryo, sans-serif",
    button: { textTransform: 'none', fontWeight: 700 }, // ボタンの英字を大文字にしない
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 999 } }, // 丸いボタン（モックアップの --radius-pill）
    },
  },
})
