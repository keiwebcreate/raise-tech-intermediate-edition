import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import VideocamOutlinedIcon from '@mui/icons-material/VideocamOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import { useEffect, useEffectEvent, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { canSubmitContent } from '../../shared/text.ts'
import { MediaDraftPreview } from './MediaDraftPreview.tsx'
import { IMAGE_ACCEPT, IMAGE_MAX_COUNT, mediaSignature, VIDEO_ACCEPT } from './mediaRules.ts'
import type { PostMedia } from './postApi.ts'
import { RemainingCount } from './RemainingCount.tsx'
import { useMediaDraft } from './useMediaDraft.ts'

type Props = {
  // 最初に入っている本文と画像・動画（編集のとき）
  initialContent?: string
  initialMedia?: PostMedia[]
  submitLabel: string
  // 送る。media は画像・動画のキー（並び順）。失敗したら ApiError を投げる（入力した内容は消さない）
  onSubmit: (content: string, media: string[]) => Promise<void>
  // 本文が変わったとき（編集の破棄の確認に使う）
  onChange?: (content: string) => void
  // 画像・動画が変わったとき。並びの印（キーを並べた文字）を渡す（編集の破棄の確認に使う）
  onMediaChange?: (signature: string) => void
  // モーダルを開いたとき、すぐ入力できるように入力欄にカーソルを置く
  focusOnOpen?: boolean
  // タイムラインの上の入力欄なら、投稿できたら空にする
  clearOnSuccess?: boolean
  inputId: string
}

// 本文の入力欄・画像と動画・残りの文字数・送るボタン（タイムラインの上の入力欄と M-01 で使い回す）
export function PostComposer({
  initialContent = '',
  initialMedia = [],
  submitLabel,
  onSubmit,
  onChange,
  onMediaChange,
  focusOnOpen,
  clearOnSuccess,
  inputId,
}: Props) {
  const [content, setContent] = useState(initialContent)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const imageInputRef = useRef<HTMLInputElement>(null)
  const videoInputRef = useRef<HTMLInputElement>(null)
  const draft = useMediaDraft(initialMedia)

  // モーダルが開いたら入力欄へ。編集のときは本文の最後にカーソルを置く
  useEffect(() => {
    const input = inputRef.current
    if (!focusOnOpen || !input) return
    input.focus()
    input.setSelectionRange(input.value.length, input.value.length)
  }, [focusOnOpen])

  // 画像・動画が変わったら知らせる
  const signature = mediaSignature(draft.items.map((item) => item.key ?? item.id))
  const notifyMediaChange = useEffectEvent((value: string) => onMediaChange?.(value))
  useEffect(() => {
    notifyMediaChange(signature)
  }, [signature])

  const canSubmit = canSubmitContent(content) && draft.ready && !busy

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setError(null)
    try {
      await onSubmit(content, draft.keys)
      if (clearOnSuccess) {
        setContent('')
        onChange?.('')
        draft.reset()
      }
    } catch (e) {
      // 入力の誤りならその文言、ほかは「投稿に失敗しました…」
      const message = e instanceof ApiError && e.errors.length > 0 ? e.errors[0].message : null
      setError(message ?? (e instanceof ApiError && e.status === 404 ? e.detail : MESSAGES.postFailed))
    } finally {
      setBusy(false)
    }
  }

  // 同じファイルをもう一度選べるように、選んだあとは入力欄を空にする
  function handleImages(event: ChangeEvent<HTMLInputElement>) {
    draft.addImages(Array.from(event.target.files ?? []))
    event.target.value = ''
  }

  function handleVideo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) draft.addVideo(file)
    event.target.value = ''
  }

  // 画像を選んでいる間は動画を、動画を選んでいる間は画像を押せない（post.md 4.1）
  const imageDisabled = busy || draft.kind === 'video' || draft.items.length >= IMAGE_MAX_COUNT
  const videoDisabled = busy || draft.items.length > 0

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
      <MediaDraftPreview draft={draft} />
      {draft.error && (
        <Alert severity="error" role="alert" onClose={draft.clearError} closeText="閉じる">
          {draft.error}
        </Alert>
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <input
          ref={imageInputRef}
          type="file"
          name="images"
          accept={IMAGE_ACCEPT}
          multiple
          hidden
          onChange={handleImages}
        />
        <input ref={videoInputRef} type="file" name="video" accept={VIDEO_ACCEPT} hidden onChange={handleVideo} />
        <Tooltip title="画像（4枚まで）">
          <span>
            <IconButton
              aria-label="画像を選ぶ"
              color="primary"
              disabled={imageDisabled}
              onClick={() => imageInputRef.current?.click()}
            >
              <ImageOutlinedIcon />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="動画（1本・MP4）">
          <span>
            <IconButton
              aria-label="動画を選ぶ"
              color="primary"
              disabled={videoDisabled}
              onClick={() => videoInputRef.current?.click()}
            >
              <VideocamOutlinedIcon />
            </IconButton>
          </span>
        </Tooltip>
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 2 }}>
          <RemainingCount text={content} />
          <Button type="submit" variant="contained" disabled={!canSubmit}>
            {submitLabel}
          </Button>
        </Box>
      </Box>
    </Box>
  )
}
