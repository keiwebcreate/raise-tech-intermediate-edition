// 投稿の入力欄（M-01・タイムラインの上。post.md 3章・4.1・4.4・5章、screen-design.md M-01）
import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MESSAGES } from '../../shared/messages.ts'
import { renderWithApp } from '../../test/render.tsx'
import { uploadImage } from './mediaApi.ts'
import { IMAGE_ACCEPT } from './mediaRules.ts'
import type { PostMedia } from './postApi.ts'
import { PostComposer } from './PostComposer.tsx'

vi.mock('./mediaApi.ts', () => ({ uploadImage: vi.fn(), requestVideoUpload: vi.fn(), putVideo: vi.fn() }))

const imageApi = vi.mocked(uploadImage)

type Props = {
  initialContent?: string
  initialMedia?: PostMedia[]
  clearOnSuccess?: boolean
  onSubmit?: (content: string, media: string[]) => Promise<void>
}

function setup(props: Props = {}) {
  const onSubmit = vi.fn(props.onSubmit ?? (async () => {}))
  const submitLabel = props.initialContent ? '保存する' : '投稿する'
  const rendered = renderWithApp(
    <PostComposer
      inputId="post-input"
      submitLabel={submitLabel}
      initialContent={props.initialContent}
      initialMedia={props.initialMedia}
      clearOnSuccess={props.clearOnSuccess}
      onSubmit={onSubmit}
    />,
  )
  return {
    ...rendered,
    onSubmit,
    box: screen.getByRole('textbox', { name: '本文' }),
    submit: screen.getByRole('button', { name: submitLabel }),
    imageInput: rendered.container.ownerDocument.querySelector<HTMLInputElement>(
      `input[type=file][accept="${IMAGE_ACCEPT}"]`,
    ),
  }
}

beforeEach(() => {
  imageApi.mockReset()
  let n = 0
  imageApi.mockImplementation(async () => {
    n += 1
    return { key: `tmp/1/img${n}.webp`, url: `https://s3/img${n}.webp` }
  })
})

describe('PostComposer：本文', () => {
  it('空・空白だけのときは「投稿する」を押せない（5章）', async () => {
    const { user, box, submit } = setup()
    expect(submit).toBeDisabled()

    await user.type(box, '  ')

    expect(submit).toBeDisabled()
  })

  it('本文を送る。画像がなければ media は空', async () => {
    const { user, box, submit, onSubmit } = setup()

    await user.type(box, '今日は Spring Boot を勉強した。')
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith('今日は Spring Boot を勉強した。', [])
  })

  it('clearOnSuccess なら、送れたあと入力欄を空にする', async () => {
    const { user, box, submit } = setup({ clearOnSuccess: true })

    await user.type(box, 'こんにちは')
    await user.click(submit)

    await waitFor(() => expect(box).toHaveValue(''))
  })

  it('残りの文字数を出し、280文字を超えたらマイナスで押せなくする（3.1）', async () => {
    const { user, box, submit } = setup()

    await user.click(box)
    await user.paste('あ'.repeat(280))
    expect(submit).toBeEnabled()

    await user.paste('い')
    expect(submit).toBeDisabled()
    expect(screen.getByText(/-1/)).toBeInTheDocument()
  })

  it('送れなかったら「投稿に失敗しました…」と出し、入力は消さない（5章）', async () => {
    const { user, box, submit } = setup({
      onSubmit: async () => {
        throw new Error('つながらない')
      },
    })

    await user.type(box, 'こんにちは')
    await user.click(submit)

    expect(await screen.findByText(MESSAGES.postFailed)).toBeInTheDocument()
    expect(box).toHaveValue('こんにちは')
  })

  it('編集のときは、今の本文が入っている（4.4）', () => {
    const { box, submit } = setup({ initialContent: '前の本文' })

    expect(box).toHaveValue('前の本文')
    expect(submit).toBeEnabled()
  })
})

describe('PostComposer：画像', () => {
  it('画像を選ぶと送り、投稿のときにキーを並べて送る', async () => {
    const { user, box, submit, onSubmit, imageInput } = setup()
    expect(imageInput).not.toBeNull()

    await user.type(box, '写真です')
    await user.upload(imageInput!, [
      new File(['1'], '1.png', { type: 'image/png' }),
      new File(['2'], '2.png', { type: 'image/png' }),
    ])
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith('写真です', ['tmp/1/img1.webp', 'tmp/1/img2.webp'])
  })

  it('画像を選んでいる間は「動画を選ぶ」を押せない（4.1）', async () => {
    const { user, imageInput } = setup()
    expect(screen.getByRole('button', { name: '動画を選ぶ' })).toBeEnabled()

    await user.upload(imageInput!, new File(['1'], '1.png', { type: 'image/png' }))

    await waitFor(() => expect(screen.getByRole('button', { name: '動画を選ぶ' })).toBeDisabled())
  })

  it('画像を送っている間は「投稿する」を押せない', async () => {
    imageApi.mockReturnValueOnce(new Promise(() => {}))
    const { user, box, submit, imageInput } = setup()

    await user.type(box, '写真です')
    await user.upload(imageInput!, new File(['1'], '1.png', { type: 'image/png' }))

    expect(await screen.findByRole('progressbar', { name: '画像 1 を送信中' })).toBeInTheDocument()
    expect(submit).toBeDisabled()
  })

  it('選んだ画像は × で外せ、外した画像は送らない（3章）', async () => {
    const { user, box, submit, onSubmit, imageInput } = setup()

    await user.type(box, '写真です')
    await user.upload(imageInput!, [
      new File(['1'], '1.png', { type: 'image/png' }),
      new File(['2'], '2.png', { type: 'image/png' }),
    ])
    await user.click(await screen.findByRole('button', { name: '画像 1 を外す' }))
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith('写真です', ['tmp/1/img2.webp'])
  })

  it('編集のときは今の画像のキーをそのまま送り返す（post.md 8章）', async () => {
    const { user, submit, onSubmit } = setup({
      initialContent: '前の本文',
      initialMedia: [{ type: 'image', key: 'posts/2026/11/a.webp', url: 'https://s3/a' }],
    })

    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith('前の本文', ['posts/2026/11/a.webp'])
  })
})
