// 文字数（docs/features/post.md 3.1）

// 本文の上限
export const CONTENT_MAX = 280

// 見た目の1文字を1文字と数える。[...text] はコードポイントごとに分けるので、ほとんどの絵文字も1文字になる
// （サーバーの codePointCount、DB の varchar と同じ数え方）
export function countChars(text: string): number {
  return [...text].length
}

// 残りの文字数の見た目：normal（ふつう）／warn（残り20文字から黄色）／over（超えたら赤）
export type RemainingLevel = 'normal' | 'warn' | 'over'

export function remainingLevel(remaining: number): RemainingLevel {
  if (remaining < 0) return 'over'
  if (remaining <= 20) return 'warn'
  return 'normal'
}

// 投稿・保存のボタンを押せるか：前後の空白を除いて1文字以上、280文字以内
export function canSubmitContent(text: string): boolean {
  const trimmed = text.trim()
  return trimmed !== '' && countChars(trimmed) <= CONTENT_MAX
}
