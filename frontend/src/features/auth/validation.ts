// 新規登録の入力チェック（docs/features/authentication.md 3.1・5章）。
// サーバーでも同じルールで確かめる。画面でも確かめるのは、送る前にすぐ誤りを知らせるため

import { MESSAGES } from '../../shared/messages.ts'

export type SignupForm = {
  email: string
  username: string
  displayName: string
  password: string
  passwordConfirm: string
}

export type SignupErrors = Partial<Record<keyof SignupForm, string>>

const EMAIL_MAX = 254
const DISPLAY_NAME_MAX = 50

// @ の前後に空白と @ 以外の文字があり、後ろに . があること（細かい形式はサーバーで確かめる）
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// 半角英数字と _ で 3〜15文字
const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,15}$/
// 半角の英数字と記号（! から ~ まで）で 8〜64文字
const PASSWORD_PATTERN = /^[\x21-\x7E]{8,64}$/

export function validateEmail(email: string): string | undefined {
  const value = email.trim()
  if (value === '') return MESSAGES.required('メールアドレス')
  if (!EMAIL_PATTERN.test(value) || value.length > EMAIL_MAX) return 'メールアドレスの形式で入力してください'
  return undefined
}

export function validateUsername(username: string): string | undefined {
  if (username === '') return MESSAGES.required('ユーザー名')
  if (!USERNAME_PATTERN.test(username)) return 'ユーザー名は3〜15文字の半角英数字と _ で入力してください'
  return undefined
}

export function validateDisplayName(displayName: string): string | undefined {
  const value = displayName.trim()
  if (value === '') return '表示名を入力してください'
  // 見た目の1文字を1文字と数える（絵文字も1文字。[...value] は文字ごとに分ける）
  if ([...value].length > DISPLAY_NAME_MAX) return '表示名は50文字以内で入力してください'
  return undefined
}

export function validatePassword(password: string): string | undefined {
  if (password === '') return MESSAGES.required('パスワード')
  if (!PASSWORD_PATTERN.test(password)) return 'パスワードは8〜64文字の半角英数字・記号で入力してください'
  return undefined
}

export function validatePasswordConfirm(password: string, passwordConfirm: string): string | undefined {
  if (passwordConfirm === '') return MESSAGES.required('パスワード（確認）')
  if (password !== passwordConfirm) return 'パスワードが一致しません'
  return undefined
}

// すべての項目を確かめて、誤りのある項目だけを返す
export function validateSignup(form: SignupForm): SignupErrors {
  const errors: SignupErrors = {
    email: validateEmail(form.email),
    username: validateUsername(form.username),
    displayName: validateDisplayName(form.displayName),
    password: validatePassword(form.password),
    passwordConfirm: validatePasswordConfirm(form.password, form.passwordConfirm),
  }
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message !== undefined)) as SignupErrors
}
