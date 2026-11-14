import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { canSubmitContent } from '../../shared/text.ts'
import { RemainingCount } from '../post/RemainingCount.tsx'

type Props = {
  inputId: string
  placeholder: string
  submitLabel: string
  // 送る。失敗したら ApiError を投げる（入力した内容は消さない）
  onSubmit: (content: string) => Promise<void>
  // 本文が変わったとき（返信の入力欄を閉じる前の、破棄の確認に使う）
  onChange?: (content: string) => void
  // 返信の入力欄なら、Esc と「キャンセル」で閉じる
  onCancel?: () => void
  // 返信の入力欄なら、返信先の @ユーザー名（入力欄の上に「返信先：@ユーザー名」と出す）
  replyToUsername?: string
  // 開いたとき、すぐ入力できるように入力欄にカーソルを置く（返信の入力欄）
  focusOnOpen?: boolean
}

// コメント・返信の入力欄（comment.md 4.1・4.5）。本文・残りの文字数・送るボタン。
// Enter は改行（コメントは改行できる）。送るのはボタンだけ
export function CommentComposer({
  inputId,
  placeholder,
  submitLabel,
  onSubmit,
  onChange,
  onCancel,
  replyToUsername,
  focusOnOpen,
}: Props) {
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // 「返信」を押して開いたら、入力欄へ（押した人が自分で開いたので、カーソルを移してよい）
  useEffect(() => {
    if (focusOnOpen) inputRef.current?.focus()
  }, [focusOnOpen])
  // 本文が空・280文字を超えたらボタンを押せない（「コメントを入力してください」の代わり。comment.md 5章）
  const canSubmit = canSubmitContent(content) && !busy

  function change(value: string) {
    setContent(value)
    onChange?.(value)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setError(null)
    try {
      await onSubmit(content)
      change('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  // Esc で閉じる。日本語の変換中の Esc は「変換をやめる」なので、閉じない
  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Escape' || event.nativeEvent.isComposing || !onCancel) return
    event.preventDefault()
    // 外側のダイアログなどに Esc を伝えない
    event.stopPropagation()
    onCancel()
  }

  return (
    <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {replyToUsername && (
        <Typography variant="body2" color="text.secondary">
          返信先：@{replyToUsername}
        </Typography>
      )}
      {error && (
        <Alert severity="error" role="alert">
          {error}
        </Alert>
      )}
      <TextField
        id={inputId}
        placeholder={placeholder}
        multiline
        minRows={onCancel ? 2 : 1}
        maxRows={10}
        fullWidth
        inputRef={inputRef}
        value={content}
        onChange={(e) => change(e.target.value)}
        onKeyDown={handleKeyDown}
        slotProps={{ htmlInput: { 'aria-label': replyToUsername ? `@${replyToUsername} への返信` : 'コメント' } }}
      />
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 1.5 }}>
        <RemainingCount text={content} />
        {onCancel && (
          <Button onClick={onCancel} disabled={busy}>
            キャンセル
          </Button>
        )}
        <Button type="submit" variant="contained" disabled={!canSubmit}>
          {submitLabel}
        </Button>
      </Box>
    </Box>
  )
}

// 入力の誤りならその文言、投稿・返信先が消されていたらその文言（comment.md 5章）、ほかは「コメントに失敗しました…」
function errorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return MESSAGES.commentFailed
  if (e.errors.length > 0) return e.errors[0].message
  if (e.status === 404) return e.detail
  return MESSAGES.commentFailed
}
