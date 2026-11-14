import { describe, expect, it } from 'vitest'
import { bioToSend, checkBio, checkDisplayName, checkIconFile, PROFILE_MESSAGES } from './profileRules.ts'

describe('checkDisplayName', () => {
  it('前後の空白を除いて空なら「表示名を入力してください」', () => {
    expect(checkDisplayName('')).toBe(PROFILE_MESSAGES.displayNameRequired)
    expect(checkDisplayName('  　 ')).toBe(PROFILE_MESSAGES.displayNameRequired)
  })
  it('50文字まではよく、51文字からはだめ。絵文字も1文字と数える', () => {
    expect(checkDisplayName('あ'.repeat(50))).toBeNull()
    expect(checkDisplayName(` ${'😀'.repeat(50)} `)).toBeNull()
    expect(checkDisplayName('あ'.repeat(51))).toBe(PROFILE_MESSAGES.displayNameMax)
  })
})

describe('checkBio', () => {
  it('空でもよく、160文字まではよい。前後の空白・改行は数えない', () => {
    expect(checkBio('')).toBeNull()
    expect(checkBio(`\n${'い'.repeat(160)}\n `)).toBeNull()
    expect(checkBio('い'.repeat(161))).toBe(PROFILE_MESSAGES.bioMax)
  })
})

describe('checkIconFile', () => {
  it('JPEG・PNG・WebP の 5MB まで。GIF はだめ', () => {
    expect(checkIconFile({ type: 'image/png', size: 5 * 1024 * 1024 })).toBeNull()
    expect(checkIconFile({ type: 'image/webp', size: 1 })).toBeNull()
    expect(checkIconFile({ type: 'image/gif', size: 1 })).toBe(PROFILE_MESSAGES.iconType)
    expect(checkIconFile({ type: 'image/jpeg', size: 5 * 1024 * 1024 + 1 })).toBe(PROFILE_MESSAGES.iconSize)
  })
})

describe('bioToSend', () => {
  it('前後の空白を除き、空なら null。途中の改行は残す', () => {
    expect(bioToSend('  \n ')).toBeNull()
    expect(bioToSend(' Java を\n勉強中 ')).toBe('Java を\n勉強中')
  })
})
