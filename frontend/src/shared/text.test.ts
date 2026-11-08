import { describe, expect, it } from 'vitest'
import { canSubmitContent, countChars, remainingLevel } from './text.ts'

describe('countChars', () => {
  it('ひらがな・英字・絵文字をどれも1文字と数える', () => {
    expect(countChars('あいう')).toBe(3)
    expect(countChars('abc')).toBe(3)
    expect(countChars('😀😀')).toBe(2)
  })
})

describe('remainingLevel', () => {
  it('残り21以上はふつう、20から0は黄色、マイナスは赤', () => {
    expect(remainingLevel(21)).toBe('normal')
    expect(remainingLevel(20)).toBe('warn')
    expect(remainingLevel(0)).toBe('warn')
    expect(remainingLevel(-1)).toBe('over')
  })
})

describe('canSubmitContent', () => {
  it('空白だけ・281文字は押せない。絵文字280文字は押せる', () => {
    expect(canSubmitContent(' \n ')).toBe(false)
    expect(canSubmitContent('あ'.repeat(281))).toBe(false)
    expect(canSubmitContent('😀'.repeat(280))).toBe(true)
    expect(canSubmitContent('  こんにちは  ')).toBe(true)
  })
})
