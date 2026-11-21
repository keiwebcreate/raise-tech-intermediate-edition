import { describe, expect, it } from 'vitest'
import { isTooLong, queryParamOf, searchText } from './searchQuery.ts'

describe('searchText', () => {
  it('前後の空白（全角も）を取り除く', () => {
    expect(searchText('  taro　')).toBe('taro')
  })

  it('先頭の @ と ＠ を取り除く', () => {
    expect(searchText('@taro')).toBe('taro')
    expect(searchText('＠taro')).toBe('taro')
    expect(searchText(' @ taro ')).toBe('taro')
  })

  it('途中や2つ目の @ は残す', () => {
    expect(searchText('ta@ro')).toBe('ta@ro')
    expect(searchText('@@taro')).toBe('@taro')
  })

  it('空白だけ・@ だけなら空', () => {
    expect(searchText('   ')).toBe('')
    expect(searchText('@')).toBe('')
  })
})

describe('isTooLong', () => {
  it('50文字まではよく、51文字は長すぎる', () => {
    expect(isTooLong('あ'.repeat(50))).toBe(false)
    expect(isTooLong('あ'.repeat(51))).toBe(true)
  })

  it('絵文字も1文字と数える', () => {
    expect(isTooLong('😀'.repeat(50))).toBe(false)
  })

  it('全角・半角をそろえる前の文字数で数える（「㍿」は1文字）', () => {
    expect(isTooLong('㍿'.repeat(50))).toBe(false)
  })
})

describe('queryParamOf', () => {
  it('空白だけなら URL に入れない', () => {
    expect(queryParamOf('  ')).toBe('')
  })

  it('打った言葉は、途中・最後の空白も含めてそのまま入れる', () => {
    expect(queryParamOf('山田 ')).toBe('山田 ')
  })
})
