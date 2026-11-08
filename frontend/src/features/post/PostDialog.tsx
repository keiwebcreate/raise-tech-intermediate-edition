import CloseIcon from '@mui/icons-material/Close'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { useState } from 'react'
import { ConfirmDialog } from '../../shared/ConfirmDialog.tsx'
import type { Post } from './postApi.ts'
import { PostComposer } from './PostComposer.tsx'

type Props = {
  // 編集する投稿。null なら新しく投稿する
  editing: Post | null
  onSubmit: (content: string) => Promise<void>
  onClose: () => void
}

// 投稿・編集のモーダル（M-01）。スマホでは全画面にする。
// 編集の内容を保存せずに閉じるときは「編集内容を破棄しますか？」と確かめる（post.md 4.4）
export function PostDialog({ editing, onSubmit, onClose }: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'))
  const [content, setContent] = useState(editing?.content ?? '')
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)

  const isEdit = editing !== null
  const dirty = isEdit && content !== editing.content

  function requestClose() {
    if (dirty) setConfirmingDiscard(true)
    else onClose()
  }

  return (
    <>
      {/* モーダルの名前は題名の文字だけにする（「閉じる」ボタンの名前を含めない） */}
      <Dialog
        open
        onClose={requestClose}
        fullScreen={fullScreen}
        fullWidth
        maxWidth="sm"
        aria-labelledby="post-dialog-title"
      >
        {/* MUI の DialogTitle は、ダイアログの名前の id を自分に付けてしまうので使わない */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, pt: 2, pb: 1 }}>
          <IconButton aria-label="閉じる" onClick={requestClose}>
            <CloseIcon />
          </IconButton>
          <Typography id="post-dialog-title" component="h2" variant="h6" sx={{ fontWeight: 700 }}>
            {isEdit ? '投稿を編集' : '投稿する'}
          </Typography>
        </Box>
        <DialogContent>
          <PostComposer
            inputId="post-dialog-content"
            initialContent={editing?.content}
            submitLabel={isEdit ? '保存する' : '投稿する'}
            onSubmit={onSubmit}
            onChange={setContent}
            focusOnOpen
          />
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={confirmingDiscard}
        title="編集内容を破棄しますか？"
        confirmLabel="破棄する"
        danger
        onConfirm={() => {
          setConfirmingDiscard(false)
          onClose()
        }}
        onCancel={() => setConfirmingDiscard(false)}
      />
    </>
  )
}
