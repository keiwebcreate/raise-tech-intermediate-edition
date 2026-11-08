// 画像・動画の決まり（docs/features/post.md 3章・5章）。
// 画面では送る前に先に確かめる（だめなものを送らずに済むように）。本当の確認はサーバーが中身を見て行う

export const IMAGE_MAX_COUNT = 4
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024
export const VIDEO_MAX_BYTES = 100 * 1024 * 1024
// 長さの上限（秒）。サーバーと同じく「140秒より長ければ断る」
export const VIDEO_MAX_SECONDS = 140

// ファイルを選ぶ画面で出す種類（accept）
export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp,image/gif'
export const VIDEO_ACCEPT = 'video/mp4'

const IMAGE_TYPES = IMAGE_ACCEPT.split(',')

export const MEDIA_MESSAGES = {
  imageMax: '画像は4枚までです',
  imageType: 'JPEG・PNG・WebP・GIF の画像を選んでください',
  imageSize: '画像は1枚5MBまでです',
  videoType: 'MP4 の動画を選んでください',
  videoLimit: '動画は100MB・2分20秒までです',
  videoFailed: '動画の送信に失敗しました',
  imageFailed: '画像の送信に失敗しました',
} as const

// ファイルの種類と大きさだけを見る。だめなら文言、よければ null
type FileLike = { type: string; size: number }

export function checkImageFile(file: FileLike): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return MEDIA_MESSAGES.imageType
  if (file.size > IMAGE_MAX_BYTES) return MEDIA_MESSAGES.imageSize
  return null
}

export function checkVideoFile(file: FileLike): string | null {
  if (file.type !== VIDEO_ACCEPT) return MEDIA_MESSAGES.videoType
  if (file.size > VIDEO_MAX_BYTES) return MEDIA_MESSAGES.videoLimit
  return null
}

// 動画の長さ。読めなかった（NaN など）ときは、動画として再生できないので種類の文言
export function checkVideoDuration(seconds: number): string | null {
  if (!Number.isFinite(seconds) || seconds <= 0) return MEDIA_MESSAGES.videoType
  if (seconds > VIDEO_MAX_SECONDS) return MEDIA_MESSAGES.videoLimit
  return null
}

// 今 current 枚あるところに adding 枚足せるか
export function checkImageCount(current: number, adding: number): string | null {
  return current + adding > IMAGE_MAX_COUNT ? MEDIA_MESSAGES.imageMax : null
}

// 画像・動画の並びの印。外す・足す・並びが変わると変わる（編集の破棄の確認に使う）
export function mediaSignature(keys: string[]): string {
  return keys.join(' ')
}

// 投稿の画像の並べ方（post.md 4.2）。CSS グリッドの「場所の名前」で表す
//   1枚：1枚を大きく ／ 2枚：左右 ／ 3枚：左に1枚大きく、右に2枚 ／ 4枚：2×2
export function gridAreas(count: number): string {
  switch (count) {
    case 1:
      return '"a"'
    case 2:
      return '"a b"'
    case 3:
      return '"a b" "a c"'
    default:
      return '"a b" "c d"'
  }
}
