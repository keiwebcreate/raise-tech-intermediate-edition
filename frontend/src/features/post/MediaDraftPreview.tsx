import CloseIcon from '@mui/icons-material/Close'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import LinearProgress from '@mui/material/LinearProgress'
import Typography from '@mui/material/Typography'
import { MEDIA_MESSAGES } from './mediaRules.ts'
import type { DraftItem, MediaDraft } from './useMediaDraft.ts'

// 投稿の前に、選んだ画像・動画を小さく並べる（screen-design.md M-01）。1つずつ × で外せる
export function MediaDraftPreview({ draft }: { draft: MediaDraft }) {
  if (draft.items.length === 0) return null
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 1 }}>
      {draft.items.map((item, index) =>
        item.kind === 'video' ? (
          <VideoTile key={item.id} item={item} draft={draft} />
        ) : (
          <ImageTile key={item.id} item={item} index={index} draft={draft} />
        ),
      )}
    </Box>
  )
}

function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <IconButton
      size="small"
      aria-label={label}
      onClick={onClick}
      sx={{
        position: 'absolute',
        top: 4,
        right: 4,
        bgcolor: 'rgba(28, 36, 82, 0.75)',
        color: '#fff',
        '&:hover': { bgcolor: 'rgba(28, 36, 82, 0.9)' },
      }}
    >
      <CloseIcon fontSize="small" />
    </IconButton>
  )
}

function ImageTile({ item, index, draft }: { item: DraftItem; index: number; draft: MediaDraft }) {
  return (
    <Box
      sx={{
        position: 'relative',
        aspectRatio: '1 / 1',
        borderRadius: 2,
        overflow: 'hidden',
        bgcolor: 'action.hover',
        display: 'grid',
        placeItems: 'center',
      }}
    >
      {item.status === 'done' && item.url ? (
        <Box
          component="img"
          src={item.url}
          alt={`画像 ${index + 1}`}
          sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        <CircularProgress size={24} aria-label={`画像 ${index + 1} を送信中`} />
      )}
      <RemoveButton label={`画像 ${index + 1} を外す`} onClick={() => draft.remove(item.id)} />
    </Box>
  )
}

function VideoTile({ item, draft }: { item: DraftItem; draft: MediaDraft }) {
  return (
    <Box sx={{ gridColumn: '1 / -1', position: 'relative' }}>
      <Box
        component="video"
        src={item.url}
        muted
        playsInline
        preload="metadata"
        aria-label="選んだ動画"
        sx={{ display: 'block', width: '100%', maxHeight: 240, borderRadius: 2, bgcolor: '#000' }}
      />
      <RemoveButton label="動画を外す" onClick={() => draft.remove(item.id)} />
      {item.status === 'uploading' && (
        <Box sx={{ mt: 1 }} aria-live="polite">
          <Typography variant="body2" color="text.secondary">
            送信中 {item.progress}%
          </Typography>
          <LinearProgress variant="determinate" value={item.progress} aria-label="動画の送信の進み具合" />
        </Box>
      )}
      {item.status === 'error' && (
        <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }} role="alert">
          <Typography variant="body2" color="error">
            {MEDIA_MESSAGES.videoFailed}
          </Typography>
          <Button size="small" variant="outlined" onClick={() => draft.retry(item.id)}>
            もう一度送る
          </Button>
        </Box>
      )}
    </Box>
  )
}
