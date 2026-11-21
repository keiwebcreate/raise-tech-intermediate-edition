// コメント・返信の入力欄（comment.md 3章・4.1・4.5、post.md 3.1）
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MESSAGES } from '../../shared/messages.ts'
import { renderWithApp } from '../../test/render.tsx'
import { CommentComposer } from './CommentComposer.tsx'

function setup(props: { replyToUsername?: string; onSubmit?: (content: string) => Promise<void> } = {}) {
  const onSubmit = vi.fn(props.onSubmit ?? (async () => {}))
  const onCancel = vi.fn()
  const rendered = renderWithApp(
    <CommentComposer
      inputId="comment-input"
      placeholder="コメントを入力"
      submitLabel={props.replyToUsername ? '返信する' : 'コメントする'}
      onSubmit={onSubmit}
      onCancel={onCancel}
      replyToUsername={props.replyToUsername}
    />,
  )
  const box = screen.getByRole('textbox', {
    name: props.replyToUsername ? `@${props.replyToUsername} への返信` : 'コメント',
  })
  const submit = screen.getByRole('button', { name: props.replyToUsername ? '返信する' : 'コメントする' })
  return { ...rendered, onSubmit, onCancel, box, submit }
}

describe('CommentComposer', () => {
  it('空・空白だけのときは送れない', async () => {
    const { user, box, submit } = setup()
    expect(submit).toBeDisabled()

    await user.type(box, '   ')

    expect(submit).toBeDisabled()
  })

  it('本文を送り、送れたら入力欄を空にする（4.1）', async () => {
    const { user, box, submit, onSubmit } = setup()

    await user.type(box, 'がんばって！')
    await user.click(submit)

    expect(onSubmit).toHaveBeenCalledWith('がんばって！')
    await waitFor(() => expect(box).toHaveValue(''))
  })

  it('280文字までは送れ、281文字で送れなくなる（絵文字も1文字）', async () => {
    const { user, box, submit } = setup()

    await user.click(box)
    await user.paste('😀'.repeat(280))
    expect(submit).toBeEnabled()

    await user.paste('あ')
    expect(submit).toBeDisabled()
    expect(screen.getByText(/-1/)).toBeInTheDocument()
  })

  it('送れなかったら「コメントに失敗しました…」と出し、入力は残す', async () => {
    const { user, box, submit } = setup({
      onSubmit: async () => {
        throw new Error('つながらない')
      },
    })

    await user.type(box, 'がんばって！')
    await user.click(submit)

    expect(await screen.findByText(MESSAGES.commentFailed)).toBeInTheDocument()
    expect(box).toHaveValue('がんばって！')
  })

  it('返信のときは「返信先：@ユーザー名」を出す（4.5）', () => {
    setup({ replyToUsername: 'hanako' })

    expect(screen.getByText(/返信先：@hanako/)).toBeInTheDocument()
  })

  it('返信の入力欄は、何も打っていなければ Esc で閉じる（onCancel）', async () => {
    const { user, box, onCancel } = setup({ replyToUsername: 'hanako' })

    await user.click(box)
    await user.keyboard('{Escape}')

    expect(onCancel).toHaveBeenCalled()
  })

  it('日本語の変換中の Esc では閉じない（変換を取り消すための Esc）', () => {
    const { box, onCancel } = setup({ replyToUsername: 'hanako' })

    fireEvent.keyDown(box, { key: 'Escape', isComposing: true })

    expect(onCancel).not.toHaveBeenCalled()
  })
})
