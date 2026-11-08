import { describe, expect, it } from 'vitest'
import {
  validateDisplayName,
  validateEmail,
  validatePassword,
  validatePasswordConfirm,
  validateSignup,
  validateUsername,
} from './validation.ts'

describe('validateEmail', () => {
  it('正しい形式なら誤りなし（前後の空白は無視する）', () => {
    expect(validateEmail(' taro@example.com ')).toBeUndefined()
  })
  it('空なら「入力してください」', () => {
    expect(validateEmail('  ')).toBe('メールアドレスを入力してください')
  })
  it('形式が違う・長すぎるなら形式の誤り', () => {
    expect(validateEmail('taro@example')).toBe('メールアドレスの形式で入力してください')
    expect(validateEmail(`${'a'.repeat(250)}@a.jp`)).toBe('メールアドレスの形式で入力してください')
  })
})

describe('validateUsername', () => {
  it.each(['abc', 'Taro_Dev', 'a'.repeat(15)])('「%s」は使える', (name) => {
    expect(validateUsername(name)).toBeUndefined()
  })
  it.each(['ab', 'a'.repeat(16), 'taro-dev', 'たろう', 'taro dev'])('「%s」は使えない', (name) => {
    expect(validateUsername(name)).toBe('ユーザー名は3〜15文字の半角英数字と _ で入力してください')
  })
})

describe('validateDisplayName', () => {
  it('空白だけなら「入力してください」', () => {
    expect(validateDisplayName('   ')).toBe('表示名を入力してください')
  })
  it('絵文字も1文字と数えて、50文字までなら使える', () => {
    expect(validateDisplayName('😀'.repeat(50))).toBeUndefined()
    expect(validateDisplayName('😀'.repeat(51))).toBe('表示名は50文字以内で入力してください')
  })
})

describe('validatePassword', () => {
  it.each(['password', 'p@ss!w0rd~', 'a'.repeat(64)])('「%s」は使える', (password) => {
    expect(validatePassword(password)).toBeUndefined()
  })
  it.each(['short7!', 'a'.repeat(65), 'pass word1', 'パスワード12345'])('「%s」は使えない', (password) => {
    expect(validatePassword(password)).toBe('パスワードは8〜64文字の半角英数字・記号で入力してください')
  })
})

describe('validatePasswordConfirm', () => {
  it('同じなら誤りなし、違えば「一致しません」', () => {
    expect(validatePasswordConfirm('password1', 'password1')).toBeUndefined()
    expect(validatePasswordConfirm('password1', 'password2')).toBe('パスワードが一致しません')
  })
})

describe('validateSignup', () => {
  it('誤りのある項目だけを返す', () => {
    expect(
      validateSignup({
        email: 'taro@example.com',
        username: 'ab',
        displayName: '山田 太郎',
        password: 'password1',
        passwordConfirm: 'password2',
      }),
    ).toEqual({
      username: 'ユーザー名は3〜15文字の半角英数字と _ で入力してください',
      passwordConfirm: 'パスワードが一致しません',
    })
  })
})
