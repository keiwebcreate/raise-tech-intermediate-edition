// 検索の言葉の整え方（docs/features/user-search.md 3章）。サーバーの UserSearchQuery と同じ決まりにする
import { countChars } from '../../shared/text.ts'

// 検索の言葉の文字数の上限
export const SEARCH_MAX = 50

export const SEARCH_TOO_LONG = '検索する言葉は50文字以内で入力してください'

// 前後の空白と、先頭の「@」（全角の「＠」も）を取り除く。「@taro」でも「taro」でも同じ
export function searchText(raw: string): string {
  const trimmed = raw.trim()
  return trimmed.startsWith('@') || trimmed.startsWith('＠') ? trimmed.slice(1).trim() : trimmed
}

// 50文字を超えたら true。絵文字も1文字と数える。全角・半角をそろえる前の言葉で数える（サーバーと同じ）
export function isTooLong(text: string): boolean {
  return countChars(text) > SEARCH_MAX
}

// URL の ?q= に入れる値。空白だけなら入れない（空の文字）。打った言葉はそのまま入れる
// （途中の空白を消すと、「山田 」まで打って止まったときに空白が消え、続けて打つと「山田太郎」になってしまう）
export function queryParamOf(input: string): string {
  return input.trim() === '' ? '' : input
}
