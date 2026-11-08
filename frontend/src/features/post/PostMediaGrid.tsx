import ButtonBase from '@mui/material/ButtonBase'
import Box from '@mui/material/Box'
import { useState } from 'react'
import { gridAreas } from './mediaRules.ts'
import { MediaViewer } from './MediaViewer.tsx'
import type { PostMedia } from './postApi.ts'

const AREAS = ['a', 'b', 'c', 'd']

// 投稿の画像・動画（post.md 4.2・screen-design.md S-03・S-04）。
// 画像は 1〜4枚の並べ方で出し、押すと大きく表示する。動画は再生ボタン付きで、自動では再生しない
export function PostMediaGrid({ media }: { media: PostMedia[] }) {
  const [viewing, setViewing] = useState<number | null>(null)
  if (media.length === 0) return null

  const video = media.find((m) => m.type === 'video')
  if (video) {
    return (
      <Box
        component="video"
        src={video.url}
        controls
        playsInline
        // 最初の画面（1コマ目）を出すために、動画の情報だけ先に読む
        preload="metadata"
        aria-label="投稿の動画"
        sx={{ display: 'block', width: '100%', maxHeight: 480, mt: 1, borderRadius: 3, bgcolor: '#000' }}
      />
    )
  }

  const images = media.filter((m) => m.type === 'image')
  // 1枚のときは、写真のもとの縦横の比率のまま出す（高さだけ上限を付ける）。2枚以上は全体を 16:9 にそろえて切り取る
  const single = images.length === 1
  return (
    <>
      <Box
        sx={{
          display: 'grid',
          gridTemplateAreas: gridAreas(images.length),
          gridAutoColumns: '1fr',
          gridAutoRows: '1fr',
          gap: '2px',
          mt: 1,
          borderRadius: 3,
          overflow: 'hidden',
          border: 1,
          borderColor: 'divider',
          aspectRatio: single ? undefined : '16 / 9',
        }}
      >
        {images.map((image, index) => (
          <ButtonBase
            key={image.key}
            aria-label={`画像 ${index + 1} / ${images.length} を大きく表示`}
            onClick={() => setViewing(index)}
            sx={{ gridArea: AREAS[index], display: 'block', minWidth: 0, minHeight: 0, bgcolor: 'action.hover' }}
          >
            <Box
              component="img"
              src={image.url}
              alt=""
              // 2枚以上は枠の大きさが決まっているので、見えるところまで来てから読む。
              // 1枚は画像の比率で高さが決まるので、先に読む（読むまで高さが分からず、画面がずれるため）
              loading={single ? 'eager' : 'lazy'}
              sx={{
                width: '100%',
                height: single ? 'auto' : '100%',
                maxHeight: single ? 510 : undefined,
                objectFit: 'cover',
                display: 'block',
              }}
            />
          </ButtonBase>
        ))}
      </Box>
      {viewing !== null && (
        <MediaViewer images={images} index={viewing} onIndexChange={setViewing} onClose={() => setViewing(null)} />
      )}
    </>
  )
}
