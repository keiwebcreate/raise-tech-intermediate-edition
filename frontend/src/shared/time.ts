// 日時の表示（要件 N-06・docs/features/timeline.md 3.1）。モックアップの relTime・fullTime と同じ決まり

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

// 今からの近さに合わせて「たった今」「3分前」「2時間前」「11月8日」「2025年12月1日」
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const date = new Date(iso)
  const diff = now.getTime() - date.getTime()
  if (diff < MINUTE) return 'たった今'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}分前`
  if (diff < DAY) return `${Math.floor(diff / HOUR)}時間前`
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}月${date.getDate()}日`
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

// 正確な日時「2026年11月8日 14:05」（マウスを乗せたとき・投稿の詳細）
export function formatFullTime(iso: string): string {
  const date = new Date(iso)
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${hh}:${mm}`
}
