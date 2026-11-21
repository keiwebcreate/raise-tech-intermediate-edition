// 確認ダイアログ（M-03。screen-design.md 4章）
import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithApp } from '../test/render.tsx'
import { ConfirmDialog } from './ConfirmDialog.tsx'

function setup(props: { open?: boolean; busy?: boolean } = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  const rendered = renderWithApp(
    <ConfirmDialog
      open={props.open ?? true}
      title="この投稿を削除しますか？"
      message="コメントといいねも消えます"
      confirmLabel="削除する"
      danger
      busy={props.busy}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  )
  return { ...rendered, onConfirm, onCancel }
}

describe('ConfirmDialog', () => {
  it('開いているときだけ、題と説明を出す', () => {
    setup()

    const dialog = screen.getByRole('dialog', { name: 'この投稿を削除しますか？' })
    expect(dialog).toHaveTextContent('コメントといいねも消えます')
  })

  it('閉じているときは出さない', () => {
    setup({ open: false })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('決めるボタンで onConfirm、キャンセルで onCancel', async () => {
    const { user, onConfirm, onCancel } = setup()

    await user.click(screen.getByRole('button', { name: '削除する' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)

    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('Esc でもキャンセルになる', async () => {
    const { user, onCancel } = setup()

    await user.keyboard('{Escape}')

    expect(onCancel).toHaveBeenCalled()
  })

  it('送っている間（busy）は、決めるボタンを押せない（2回送らない）', () => {
    setup({ busy: true })

    expect(screen.getByRole('button', { name: '削除する' })).toBeDisabled()
  })
})
