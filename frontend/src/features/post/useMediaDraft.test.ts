// 投稿・編集で選んだ画像・動画のフック（post.md 3章・4.1・4.4・5章）
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { requestVideoUpload, putVideo, uploadImage } from './mediaApi.ts'
import { MEDIA_MESSAGES } from './mediaRules.ts'
import { useMediaDraft } from './useMediaDraft.ts'

vi.mock('./mediaApi.ts', () => ({ uploadImage: vi.fn(), requestVideoUpload: vi.fn(), putVideo: vi.fn() }))

const imageApi = vi.mocked(uploadImage)
const videoUrlApi = vi.mocked(requestVideoUpload)
const putApi = vi.mocked(putVideo)

const MB = 1024 * 1024

function fileOf(name: string, type: string, size = 1000): File {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

const png = (name = 'a.png', size?: number) => fileOf(name, 'image/png', size)
const mp4 = (size = 10 * MB) => fileOf('v.mp4', 'video/mp4', size)

// 外から返事を出せる Promise（送信中の状態を確かめるため）
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

// jsdom は動画を読まないので、<video> の長さ（秒）を決め、読み終えた知らせ（loadedmetadata）を出す
let videoSeconds = 30
const original = {
  duration: Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'duration'),
  src: Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src'),
}

beforeEach(() => {
  imageApi.mockReset()
  videoUrlApi.mockReset()
  putApi.mockReset()
  let n = 0
  imageApi.mockImplementation(async () => {
    n += 1
    return { key: `tmp/1/img${n}.webp`, url: `https://s3/img${n}.webp` }
  })
  videoUrlApi.mockResolvedValue({ key: 'tmp/1/v.mp4', uploadUrl: 'https://s3/upload' })
  putApi.mockResolvedValue()
  videoSeconds = 30
  Object.defineProperty(HTMLMediaElement.prototype, 'duration', { configurable: true, get: () => videoSeconds })
  Object.defineProperty(HTMLMediaElement.prototype, 'src', {
    configurable: true,
    get(this: HTMLMediaElement) {
      return this.getAttribute('src') ?? ''
    },
    set(this: HTMLMediaElement, value: string) {
      this.setAttribute('src', value)
      setTimeout(() => this.dispatchEvent(new Event('loadedmetadata')), 0)
    },
  })
})

afterEach(() => {
  if (original.duration) Object.defineProperty(HTMLMediaElement.prototype, 'duration', original.duration)
  if (original.src) Object.defineProperty(HTMLMediaElement.prototype, 'src', original.src)
})

describe('useMediaDraft：はじめ', () => {
  it('何も選んでいなければ空で、投稿できる', () => {
    const { result } = renderHook(() => useMediaDraft())

    expect(result.current.items).toEqual([])
    expect(result.current.kind).toBeNull()
    expect(result.current.ready).toBe(true)
    expect(result.current.keys).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('編集のときは、今の画像を送り終えたものとして並べる（キーはそのまま送り返す）', () => {
    const { result } = renderHook(() =>
      useMediaDraft([
        { type: 'image', key: 'posts/2026/11/a.webp', url: 'https://s3/a' },
        { type: 'image', key: 'posts/2026/11/b.webp', url: 'https://s3/b' },
      ]),
    )

    expect(result.current.kind).toBe('image')
    expect(result.current.ready).toBe(true)
    expect(result.current.keys).toEqual(['posts/2026/11/a.webp', 'posts/2026/11/b.webp'])
    expect(result.current.items.every((item) => item.status === 'done')).toBe(true)
  })
})

describe('useMediaDraft：画像', () => {
  it('選んだらすぐ送り、送り終えたら選んだ順のキーで投稿できる', async () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addImages([png('1.png'), png('2.png')]))

    expect(result.current.kind).toBe('image')
    await waitFor(() => expect(result.current.ready).toBe(true))
    expect(imageApi).toHaveBeenCalledTimes(2)
    expect(result.current.keys).toEqual(['tmp/1/img1.webp', 'tmp/1/img2.webp'])
  })

  it('送っている間は投稿できない（ready が false）', async () => {
    const pending = deferred<{ key: string; url: string }>()
    imageApi.mockReturnValueOnce(pending.promise)
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addImages([png()]))

    expect(result.current.items[0].status).toBe('uploading')
    expect(result.current.ready).toBe(false)
    await act(async () => pending.resolve({ key: 'tmp/1/x.webp', url: 'https://s3/x' }))
    expect(result.current.ready).toBe(true)
  })

  it('5枚目は選べず「画像は4枚までです」', async () => {
    const { result } = renderHook(() => useMediaDraft())
    act(() => result.current.addImages([png('1.png'), png('2.png'), png('3.png'), png('4.png')]))
    await waitFor(() => expect(result.current.ready).toBe(true))

    act(() => result.current.addImages([png('5.png')]))

    expect(result.current.error).toBe(MEDIA_MESSAGES.imageMax)
    expect(result.current.items).toHaveLength(4)
    expect(imageApi).toHaveBeenCalledTimes(4)
  })

  it('画像でないファイルは送らず「JPEG・PNG・WebP・GIF の画像を選んでください」', () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addImages([fileOf('a.bmp', 'image/bmp')]))

    expect(result.current.error).toBe(MEDIA_MESSAGES.imageType)
    expect(imageApi).not.toHaveBeenCalled()
  })

  it('5MB を超える画像は送らず「画像は1枚5MBまでです」', () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addImages([png('big.png', 5 * MB + 1)]))

    expect(result.current.error).toBe(MEDIA_MESSAGES.imageSize)
    expect(imageApi).not.toHaveBeenCalled()
  })

  it('送るのに失敗したら、その画像を外して「画像の送信に失敗しました」（5章）', async () => {
    imageApi.mockRejectedValueOnce(new Error('つながらない'))
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addImages([png()]))

    await waitFor(() => expect(result.current.error).toBe(MEDIA_MESSAGES.imageFailed))
    expect(result.current.items).toEqual([])
  })

  it('× で外すと、その画像のキーは送らない。全部外せば種類も空に戻る', async () => {
    const { result } = renderHook(() => useMediaDraft())
    act(() => result.current.addImages([png('1.png'), png('2.png')]))
    await waitFor(() => expect(result.current.ready).toBe(true))

    act(() => result.current.remove(result.current.items[0].id))
    expect(result.current.keys).toEqual(['tmp/1/img2.webp'])

    act(() => result.current.remove(result.current.items[0].id))
    expect(result.current.kind).toBeNull()
  })

  it('reset で空に戻り、clearError で文言が消える', async () => {
    const { result } = renderHook(() => useMediaDraft())
    act(() => result.current.addImages([png()]))
    await waitFor(() => expect(result.current.ready).toBe(true))
    act(() => result.current.addImages([fileOf('a.bmp', 'image/bmp')]))
    expect(result.current.error).not.toBeNull()

    act(() => result.current.clearError())
    expect(result.current.error).toBeNull()

    act(() => result.current.reset())
    expect(result.current.items).toEqual([])
    expect(result.current.kind).toBeNull()
  })
})

describe('useMediaDraft：動画', () => {
  it('選んだら送り先をもらって S3 へ直接送り、進み具合を出し、終わったらキーで投稿できる', async () => {
    const pending = deferred<void>()
    let report: (percent: number) => void = () => {}
    putApi.mockImplementationOnce((_url, _file, onProgress) => {
      report = onProgress
      return pending.promise
    })
    const { result } = renderHook(() => useMediaDraft())
    const file = mp4()

    act(() => result.current.addVideo(file))

    await waitFor(() => expect(putApi).toHaveBeenCalled())
    expect(videoUrlApi).toHaveBeenCalledWith(file.size)
    expect(putApi.mock.calls[0][0]).toBe('https://s3/upload')
    expect(putApi.mock.calls[0][1]).toBe(file)
    expect(result.current.kind).toBe('video')
    expect(result.current.ready).toBe(false)

    act(() => report(45))
    expect(result.current.items[0].progress).toBe(45)

    await act(async () => pending.resolve())
    expect(result.current.ready).toBe(true)
    expect(result.current.keys).toEqual(['tmp/1/v.mp4'])
  })

  it('MP4 でなければ送らず「MP4 の動画を選んでください」', async () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(fileOf('v.mov', 'video/quicktime')))

    await waitFor(() => expect(result.current.error).toBe(MEDIA_MESSAGES.videoType))
    expect(videoUrlApi).not.toHaveBeenCalled()
  })

  it('100MB を超えたら送らず「動画は100MB・2分20秒までです」', async () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4(100 * MB + 1)))

    await waitFor(() => expect(result.current.error).toBe(MEDIA_MESSAGES.videoLimit))
    expect(videoUrlApi).not.toHaveBeenCalled()
  })

  it('140秒を超えたら送らず「動画は100MB・2分20秒までです」', async () => {
    videoSeconds = 141
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))

    await waitFor(() => expect(result.current.error).toBe(MEDIA_MESSAGES.videoLimit))
    expect(videoUrlApi).not.toHaveBeenCalled()
  })

  it('140秒ちょうどなら送る', async () => {
    videoSeconds = 140
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))

    await waitFor(() => expect(result.current.keys).toEqual(['tmp/1/v.mp4']))
    expect(result.current.ready).toBe(true)
  })

  it('選んだ直後（長さを読んでいる間）から並べ、投稿できない状態にする（#53 で見つかった不具合）', () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))

    expect(result.current.kind).toBe('video')
    expect(result.current.ready).toBe(false)
    // まだ送っていないので「送信中」ではなく「確かめ中」
    expect(result.current.items[0].status).toBe('checking')
  })

  it('長すぎたときは、並べた動画を外す', async () => {
    videoSeconds = 141
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))

    await waitFor(() => expect(result.current.error).toBe(MEDIA_MESSAGES.videoLimit))
    expect(result.current.items).toEqual([])
    expect(result.current.kind).toBeNull()
  })

  it('長さを読んでいる間に外したら、送らない', async () => {
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))
    act(() => result.current.remove(result.current.items[0].id))
    // 長さを読み終える（loadedmetadata）まで待つ
    await act(async () => new Promise((resolve) => setTimeout(resolve, 10)))

    expect(videoUrlApi).not.toHaveBeenCalled()
    expect(result.current.items).toEqual([])
  })

  it('送るのに失敗したら「動画の送信に失敗しました」で投稿できず、retry で送り直せる', async () => {
    putApi.mockRejectedValueOnce(new Error('つながらない'))
    const { result } = renderHook(() => useMediaDraft())

    act(() => result.current.addVideo(mp4()))

    await waitFor(() => expect(result.current.items[0]?.status).toBe('error'))
    expect(result.current.ready).toBe(false)

    act(() => result.current.retry(result.current.items[0].id))

    await waitFor(() => expect(result.current.ready).toBe(true))
    expect(result.current.keys).toEqual(['tmp/1/v.mp4'])
  })
})
