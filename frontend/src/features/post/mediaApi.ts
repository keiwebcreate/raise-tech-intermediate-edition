// 画像・動画を選んだときの API（docs/features/post.md 8章）
import { create } from 'axios'
import { api } from '../../api/client.ts'
import { VIDEO_ACCEPT } from './mediaRules.ts'

// 画像を1枚送る。サーバーが確かめて WebP にし、一時置き場に置く。signal で途中でやめられる
export async function uploadImage(file: File, signal: AbortSignal): Promise<{ key: string; url: string }> {
  const form = new FormData()
  form.append('file', file)
  return (await api.post<{ key: string; url: string }>('/api/media/images', form, { signal })).data
}

// 動画を S3 へ直接送るための、期限付きの送り先 URL をもらう
export async function requestVideoUpload(size: number): Promise<{ key: string; uploadUrl: string }> {
  return (await api.post<{ key: string; uploadUrl: string }>('/api/media/videos', { size })).data
}

// S3 へ直接送るための道具。自分のサーバーではないので、共通の api（Cookie・CSRF・ログインの延長つき）は使わない。
// 別の住所なので、Cookie も CSRF のヘッダーも付かない（付けてはいけない）
const s3 = create()

// 動画を期限付きの URL へ PUT で送る。onProgress には 0〜100 を渡す。signal で途中でやめられる
export async function putVideo(
  uploadUrl: string,
  file: File,
  onProgress: (percent: number) => void,
  signal: AbortSignal,
): Promise<void> {
  await s3.put(uploadUrl, file, {
    // サーバーが署名に入れた種類と同じにする（違うと本物の S3 は断る）
    headers: { 'Content-Type': VIDEO_ACCEPT },
    signal,
    onUploadProgress: (event) => {
      if (event.total) onProgress(Math.floor((event.loaded / event.total) * 100))
    },
  })
}
