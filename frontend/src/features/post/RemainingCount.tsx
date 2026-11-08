import Typography from '@mui/material/Typography'
import { CONTENT_MAX, countChars, remainingLevel } from '../../shared/text.ts'

// 残りの文字数（要件 N-05・post.md 3.1）。残り20文字から黄色、超えたら赤でマイナス表示
export function RemainingCount({ text }: { text: string }) {
  const remaining = CONTENT_MAX - countChars(text.trim())
  const level = remainingLevel(remaining)
  const color = level === 'over' ? 'error.main' : level === 'warn' ? 'warning.main' : 'text.secondary'
  return (
    <Typography
      variant="body2"
      aria-live="polite"
      sx={{ color, fontWeight: level === 'normal' ? 400 : 700, fontVariantNumeric: 'tabular-nums' }}
    >
      残り {remaining}
    </Typography>
  )
}
