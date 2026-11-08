import { startTransition, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { setSessionExpiredHandler } from '../../api/client.ts'
import type { UserResponse } from '../../api/types.ts'
import { MESSAGES } from '../../shared/messages.ts'
import { AuthContext, type LoginPageState } from './AuthContext.ts'
import * as authApi from './authApi.ts'

// ログインしている本人を持ち、AuthContext で中の画面に配る
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null | undefined>(undefined)
  const navigate = useNavigate()
  const location = useLocation()

  // 期限切れの処理の中から「今ログインしているか」「今どの画面か」を見るために、最新の値を覚えておく
  const userRef = useRef(user)
  const pathRef = useRef(location.pathname + location.search)
  useEffect(() => {
    userRef.current = user
    pathRef.current = location.pathname + location.search
  }, [user, location])

  // アプリを開いたとき（再読み込みも）に1回だけ、ログインしているかを確かめる。
  // アクセストークンが切れていても、client.ts がリフレッシュトークンで延ばしてから答える
  useEffect(() => {
    authApi
      .getMe()
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  // 「戻る」「進む」で、ブラウザが凍らせておいたページ（バックフォワードキャッシュ）をそのまま出したときは、
  // ログインしているかを確かめ直す。ログアウトした後に戻ると、前の人の名前などが見えてしまうため（イシュー #17）
  useEffect(() => {
    function handlePageShow(event: PageTransitionEvent) {
      if (!event.persisted) return // ふつうに読み込んだとき（上の確認で足りる）
      setUser(undefined) // 確かめ終わるまで、前の画面を隠して「読み込み中」にする
      authApi
        .getMe()
        .then(setUser)
        .catch(() => setUser(null)) // ログインしていなければ、RequireAuth が /login へ移す
    }
    window.addEventListener('pageshow', handlePageShow)
    return () => window.removeEventListener('pageshow', handlePageShow)
  }, [])

  // 操作中にログインの延長もできなくなったら、お知らせを付けてログイン画面へ（authentication.md 4.4）
  useEffect(() => {
    setSessionExpiredHandler(() => {
      if (!userRef.current) return // もともとログインしていない（アプリを開いたときの確認など）
      const state: LoginPageState = { notice: MESSAGES.sessionExpired, from: pathRef.current }
      // 画面の移動と setUser(null) を一緒に反映させる。
      // 別々だと先に「ログインしていない」が反映され、RequireAuth がお知らせなしで /login へ移してしまう
      startTransition(() => {
        navigate('/login', { replace: true, state })
        setUser(null)
      })
    })
    return () => setSessionExpiredHandler(null)
  }, [navigate])

  const loggedIn = useCallback(
    (loggedInUser: UserResponse, next = '/') => {
      // 画面の移動と一緒に反映させる（GuestOnly が先に / へ移してしまわないように）
      startTransition(() => {
        navigate(next, { replace: true })
        setUser(loggedInUser)
      })
    },
    [navigate],
  )

  const logout = useCallback(async () => {
    // 通信に失敗しても、画面はログアウトした状態にする
    await authApi.logout().catch(() => {})
    startTransition(() => {
      navigate('/login', { replace: true })
      setUser(null)
    })
  }, [navigate])

  return <AuthContext value={{ user, loggedIn, logout }}>{children}</AuthContext>
}
