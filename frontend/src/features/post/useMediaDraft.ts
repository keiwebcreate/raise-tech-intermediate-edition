// 投稿・編集のときに選んだ画像・動画（docs/features/post.md 4.1・4.4）。
// 選んだらすぐ送り始め、一時置き場のキーを受け取る。投稿のときは、そのキーを並べて送るだけ
import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/client.ts'
import * as mediaApi from './mediaApi.ts'
import { checkImageCount, checkImageFile, checkVideoDuration, checkVideoFile, MEDIA_MESSAGES } from './mediaRules.ts'
import type { PostMedia } from './postApi.ts'

export type DraftItem = {
  // 画面の中だけの印（React の key・外すとき）
  id: string
  kind: 'image' | 'video'
  // uploading：送信中 ／ done：送り終えた（key がある）／ error：失敗（動画だけ。「もう一度送る」を出す）
  status: 'uploading' | 'done' | 'error'
  // 送信の進み具合（0〜100。動画だけ）
  progress: number
  // S3 のキー（送り終えたら入る）
  key?: string
  // 小さく表示するための URL（画像はサーバーが返した URL、新しい動画は手元のファイルの URL）
  url?: string
  // もう一度送るための元のファイル（新しい動画だけ）
  file?: File
}

export type MediaDraft = {
  items: DraftItem[]
  // 画像・動画を選んだときの誤り（「画像は4枚までです」など）
  error: string | null
  kind: 'image' | 'video' | null
  // すべて送り終えたか（送信中・失敗があれば投稿できない）
  ready: boolean
  // 投稿のときに送るキー（並び順）
  keys: string[]
  addImages: (files: File[]) => void
  addVideo: (file: File) => void
  retry: (id: string) => void
  remove: (id: string) => void
  // 投稿できたあと、空にする
  reset: () => void
  // 誤りの文言を消す
  clearError: () => void
}

function fromPost(media: PostMedia[]): DraftItem[] {
  return media.map((m) => ({ id: m.key, kind: m.type, status: 'done', progress: 100, key: m.key, url: m.url }))
}

// 動画の長さ（秒）を読む。読めなければ NaN
function readDuration(url: string): Promise<number> {
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => resolve(video.duration)
    video.onerror = () => resolve(Number.NaN)
    video.src = url
  })
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.errors.length > 0 ? error.errors[0].message : fallback
}

export function useMediaDraft(initial: PostMedia[] = []): MediaDraft {
  const [items, setItems] = useState<DraftItem[]>(() => fromPost(initial))
  const [error, setError] = useState<string | null>(null)
  // 送信中の動画を途中でやめるための道具と、手元のファイルの URL（使い終わったら片付ける）
  const aborts = useRef(new Map<string, AbortController>())
  const objectUrls = useRef(new Map<string, string>())

  const update = useCallback((id: string, change: Partial<DraftItem>) => {
    // 送っている間に外されていたら、何もしない（map で見つからない）
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...change } : item)))
  }, [])

  const release = useCallback((id: string) => {
    aborts.current.get(id)?.abort()
    aborts.current.delete(id)
    const url = objectUrls.current.get(id)
    if (url) URL.revokeObjectURL(url)
    objectUrls.current.delete(id)
  }, [])

  // 画面を閉じたら、送信中の動画をやめ、手元のファイルの URL を片付ける
  useEffect(() => {
    const abortMap = aborts.current
    const urlMap = objectUrls.current
    return () => {
      abortMap.forEach((controller) => controller.abort())
      urlMap.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [])

  const addImages = useCallback(
    (files: File[]) => {
      setError(null)
      if (files.length === 0) return
      const countError = checkImageCount(items.length, files.length)
      const fileError = files.map(checkImageFile).find((message) => message !== null)
      if (countError ?? fileError) {
        setError(countError ?? fileError ?? null)
        return
      }
      const added = files.map((file) => ({ file, id: crypto.randomUUID() }))
      setItems((current) => [
        ...current,
        ...added.map(({ id }): DraftItem => ({ id, kind: 'image', status: 'uploading', progress: 0 })),
      ])
      for (const { file, id } of added) {
        mediaApi
          .uploadImage(file)
          .then(({ key, url }) => update(id, { status: 'done', progress: 100, key, url }))
          .catch((e: unknown) => {
            // 画像は選び直してもらう（その1枚を外して文言を出す）
            setItems((current) => current.filter((item) => item.id !== id))
            setError(messageOf(e, MEDIA_MESSAGES.imageFailed))
          })
      }
    },
    [items.length, update],
  )

  const sendVideo = useCallback(
    async (id: string, file: File) => {
      const controller = new AbortController()
      aborts.current.set(id, controller)
      update(id, { status: 'uploading', progress: 0 })
      try {
        const { key, uploadUrl } = await mediaApi.requestVideoUpload(file.size)
        await mediaApi.putVideo(uploadUrl, file, (progress) => update(id, { progress }), controller.signal)
        update(id, { status: 'done', progress: 100, key })
      } catch (e) {
        if (controller.signal.aborted) return
        // 大きさで断られたときはその文言。送信の失敗なら「もう一度送る」を出す
        const message = messageOf(e, '')
        if (message) {
          setItems((current) => current.filter((item) => item.id !== id))
          setError(message)
        } else {
          update(id, { status: 'error' })
        }
      } finally {
        aborts.current.delete(id)
      }
    },
    [update],
  )

  const addVideo = useCallback(
    (file: File) => {
      setError(null)
      const fileError = checkVideoFile(file)
      if (fileError) {
        setError(fileError)
        return
      }
      const url = URL.createObjectURL(file)
      void readDuration(url).then((seconds) => {
        const durationError = checkVideoDuration(seconds)
        if (durationError) {
          URL.revokeObjectURL(url)
          setError(durationError)
          return
        }
        const id = crypto.randomUUID()
        objectUrls.current.set(id, url)
        setItems((current) => [...current, { id, kind: 'video', status: 'uploading', progress: 0, url, file }])
        void sendVideo(id, file)
      })
    },
    [sendVideo],
  )

  const retry = useCallback(
    (id: string) => {
      const item = items.find((i) => i.id === id)
      if (item?.file) void sendVideo(id, item.file)
    },
    [items, sendVideo],
  )

  const remove = useCallback(
    (id: string) => {
      release(id)
      setError(null)
      setItems((current) => current.filter((item) => item.id !== id))
    },
    [release],
  )

  const reset = useCallback(() => {
    items.forEach((item) => release(item.id))
    setItems([])
    setError(null)
  }, [items, release])

  const clearError = useCallback(() => setError(null), [])

  return {
    items,
    error,
    kind: items[0]?.kind ?? null,
    ready: items.every((item) => item.status === 'done'),
    keys: items.flatMap((item) => (item.key ? [item.key] : [])),
    addImages,
    addVideo,
    retry,
    remove,
    reset,
    clearError,
  }
}
