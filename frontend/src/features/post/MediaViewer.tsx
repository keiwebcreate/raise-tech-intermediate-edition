import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CloseIcon from '@mui/icons-material/Close'
import Box from '@mui/material/Box'
import Dialog from '@mui/material/Dialog'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import type { KeyboardEvent } from 'react'
import type { PostMedia } from './postApi.ts'

type Props = {
  images: PostMedia[]
  index: number
  onIndexChange: (index: number) => void
  onClose: () => void
}

const buttonSx = { color: '#fff', bgcolor: 'rgba(0, 0, 0, 0.5)', '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.7)' } }

// 画像を大きく表示する（post.md 4.2）。左右の矢印（キーボードの ← → でも）で他の画像に移る。Esc か × で閉じる
export function MediaViewer({ images, index, onIndexChange, onClose }: Props) {
  const hasPrev = index > 0
  const hasNext = index < images.length - 1

  function handleKeyDown(event: KeyboardEvent) {
    if (event.key === 'ArrowLeft' && hasPrev) onIndexChange(index - 1)
    if (event.key === 'ArrowRight' && hasNext) onIndexChange(index + 1)
  }

  return (
    <Dialog
      open
      fullScreen
      onClose={onClose}
      onKeyDown={handleKeyDown}
      aria-label={`画像 ${index + 1} / ${images.length}`}
      slotProps={{ paper: { sx: { bgcolor: 'rgba(10, 14, 36, 0.94)' } } }}
    >
      <Box sx={{ position: 'relative', width: '100%', height: '100%', display: 'grid', placeItems: 'center' }}>
        <Box
          component="img"
          src={images[index].url}
          alt={`画像 ${index + 1} / ${images.length}`}
          // 画面の幅・高さに収める（dvh：スマホのアドレスバーを除いた高さ）
          sx={{ maxWidth: '100vw', maxHeight: '100dvh', objectFit: 'contain', display: 'block' }}
        />
        <IconButton aria-label="閉じる" onClick={onClose} sx={{ ...buttonSx, position: 'absolute', top: 12, left: 12 }}>
          <CloseIcon />
        </IconButton>
        {images.length > 1 && (
          <Typography
            aria-hidden
            sx={{ position: 'absolute', top: 18, left: '50%', transform: 'translateX(-50%)', color: '#fff' }}
          >
            {index + 1} / {images.length}
          </Typography>
        )}
        {hasPrev && (
          <IconButton
            aria-label="前の画像"
            onClick={() => onIndexChange(index - 1)}
            sx={{ ...buttonSx, position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }}
          >
            <ChevronLeftIcon />
          </IconButton>
        )}
        {hasNext && (
          <IconButton
            aria-label="次の画像"
            onClick={() => onIndexChange(index + 1)}
            sx={{ ...buttonSx, position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)' }}
          >
            <ChevronRightIcon />
          </IconButton>
        )}
      </Box>
    </Dialog>
  )
}
