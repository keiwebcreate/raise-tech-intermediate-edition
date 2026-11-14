// プロフィールの編集の決まり（docs/features/profile.md 3.2・5章）。
// 画面では送る前に先に確かめる。本当の確認はサーバーが行う（文字数の数え方は同じ）
import { countChars } from '../../shared/text.ts'

export const DISPLAY_NAME_MAX = 50
export const BIO_MAX = 160
export const ICON_MAX_BYTES = 5 * 1024 * 1024
// ファイルを選ぶ画面で出す種類（accept）。投稿の画像と違い、GIF は使えない
export const ICON_ACCEPT = 'image/jpeg,image/png,image/webp'

const ICON_TYPES = ICON_ACCEPT.split(',')

export const PROFILE_MESSAGES = {
  displayNameRequired: '表示名を入力してください',
  displayNameMax: '表示名は50文字以内で入力してください',
  bioMax: '自己紹介は160文字以内で入力してください',
  iconType: 'JPEG・PNG・WebP の画像を選んでください',
  iconSize: '画像は5MBまでです',
  iconFailed: '画像の送信に失敗しました',
} as const

// 表示名。前後の空白を除いて 1〜50文字。だめなら文言、よければ null
export function checkDisplayName(value: string): string | null {
  const trimmed = value.trim()
  if (trimmed === '') return PROFILE_MESSAGES.displayNameRequired
  if (countChars(trimmed) > DISPLAY_NAME_MAX) return PROFILE_MESSAGES.displayNameMax
  return null
}

// 自己紹介。前後の空白・改行を除いて 160文字まで（空でもよい）
export function checkBio(value: string): string | null {
  return countChars(value.trim()) > BIO_MAX ? PROFILE_MESSAGES.bioMax : null
}

// アイコンにするファイルの種類と大きさ
export function checkIconFile(file: { type: string; size: number }): string | null {
  if (!ICON_TYPES.includes(file.type)) return PROFILE_MESSAGES.iconType
  if (file.size > ICON_MAX_BYTES) return PROFILE_MESSAGES.iconSize
  return null
}

// 送る自己紹介。空なら null（自己紹介なし）
export function bioToSend(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}
