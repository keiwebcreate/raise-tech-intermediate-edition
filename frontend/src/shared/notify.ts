// 画面の下に少しの間だけ出すお知らせ（screen-design.md 3.4）を出す関数の入れ物。
// 中身は NotificationProvider（notification.tsx）が用意する
import { createContext, useContext } from 'react'

export type Notify = (message: string) => void

export const NotifyContext = createContext<Notify | null>(null)

export function useNotify(): Notify {
  const value = useContext(NotifyContext)
  if (!value) throw new Error('useNotify は NotificationProvider の中で使ってください')
  return value
}
