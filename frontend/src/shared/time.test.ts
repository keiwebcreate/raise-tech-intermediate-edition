import { describe, expect, it } from 'vitest'
import { formatFullTime, formatRelativeTime } from './time.ts'

// 日本時間の 2026年11月8日 14:05 を「今」とする
const now = new Date(2026, 10, 8, 14, 5, 0)
const before = (ms: number) => new Date(now.getTime() - ms).toISOString()

describe('formatRelativeTime', () => {
  it('1分より前は「たった今」、1時間より前は「◯分前」、1日より前は「◯時間前」', () => {
    expect(formatRelativeTime(before(30 * 1000), now)).toBe('たった今')
    expect(formatRelativeTime(before(3 * 60 * 1000), now)).toBe('3分前')
    expect(formatRelativeTime(before(2 * 60 * 60 * 1000), now)).toBe('2時間前')
  })
  it('1日以上前は、今年なら「◯月◯日」、去年より前なら年も付ける', () => {
    expect(formatRelativeTime(new Date(2026, 9, 1, 9, 0).toISOString(), now)).toBe('10月1日')
    expect(formatRelativeTime(new Date(2025, 11, 1, 9, 0).toISOString(), now)).toBe('2025年12月1日')
  })
})

describe('formatFullTime', () => {
  it('「2026年11月8日 09:05」の形（時・分は2けた）', () => {
    expect(formatFullTime(new Date(2026, 10, 8, 9, 5).toISOString())).toBe('2026年11月8日 09:05')
  })
})
