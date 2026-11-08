import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { canSubmitContent } from '../../shared/text.ts'
import { RemainingCount } from './RemainingCount.tsx'

type Props = {
  // 最初に入っている本文（編集のとき）
  initialContent?: string
  submitLabel: string
  // 送る。失敗したら ApiError を投げる（入力した内容は消さない）
  onSubmit: (content: string) => Promise<void>
  // 入力が変わったとき（編集の破棄の確認に使う）
  onChange?: (content: string) => void
  // モーダルを開いたとき、すぐ入力できるように入力欄にカーソルを置く
  focusOnOpen?: boolean
  // タイムラインの上の入力欄なら、投稿できたら空にする
  clearOnSuccess?: boolean
  inputId: string
}

// 本文の入力欄・残りの文字数・送るボタン（タイムラインの上の入力欄と M-01 で使い回す）
export function PostComposer({
  initialContent = '',
  submitLabel,
  onSubmit,
  onChange,
  focusOnOpen,
  clearOnSuccess,
  inputId,
}: Props) {
  const [content, setContent] = useState(initialContent)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // モーダルが開いたら入力欄へ。編集のときは本文の最後にカーソルを置く
  useEffect(() => {
    const input = inputRef.current
    if (!focusOnOpen || !input) return
    input.focus()
    input.setSelectionRange(input.value.length, input.value.length)
  }, [focusOnOpen])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmitContent(content) || busy) return
    setBusy(true)
    setError(null)
    try {
      await onSubmit(content)
      if (clearOnSuccess) {
        setContent('')
        onChange?.('')
      }
    } catch (e) {
      // 入力の誤りならその文言、ほかは「投稿に失敗しました…」
      const message = e instanceof ApiError && e.errors.length > 0 ? e.errors[0].message : null
      setError(message ?? (e instanceof ApiError && e.status === 404 ? e.detail : MESSAGES.postFailed))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {error && (
        <Alert severity="error" role="alert">
          {error}
        </Alert>
      )}
      <TextField
        id={inputId}
        placeholder="いまどうしてる？"
        multiline
        minRows={2}
        maxRows={12}
        fullWidth
        inputRef={inputRef}
        value={content}
        onChange={(e) => {
          setContent(e.target.value)
          onChange?.(e.target.value)
        }}
        slotProps={{ htmlInput: { 'aria-label': '本文' } }}
      />
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 2 }}>
        <RemainingCount text={content} />
        <Button type="submit" variant="contained" disabled={!canSubmitContent(content) || busy}>
          {submitLabel}
        </Button>
      </Box>
    </Box>
  )
}
