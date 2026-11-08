// ログインしている本人を、どの画面からでも使えるようにする入れ物。中身は AuthProvider が用意する
import { createContext, useContext } from 'react'
import type { UserResponse } from '../../api/types.ts'

type AuthContextValue = {
  // undefined：確かめている途中 ／ null：ログインしていない ／ それ以外：ログインしている本人
  user: UserResponse | null | undefined
  // 新規登録・ログインが成功したときに呼ぶ。元の画面（なければ /）へ移る
  loggedIn: (user: UserResponse, next?: string) => void
  // ログアウトして、ログイン画面へ移る
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth は AuthProvider の中で使ってください')
  }
  return value
}

// ログイン画面に渡すもの（React Router の state）
export type LoginPageState = {
  // 1回だけ出すお知らせ（ログインの期限切れなど）
  notice?: string
  // ログインの前に開こうとしていた画面。ログインしたらそこへ戻す（authentication.md 4.2）
  from?: string
}
