// API の返事の形（docs/features/authentication.md 8章）

// ログインしている本人
export type UserResponse = {
  id: number
  email: string
  username: string
  displayName: string
  // アイコンを見るための期限付きの URL（期限1時間）。未設定なら null
  iconUrl: string | null
}

// 投稿・コメント・いいねした人などに出てくるユーザー（profile.md 8章）
export type UserSummary = {
  id: number
  username: string
  displayName: string
  // アイコンを見るための期限付きの URL。未設定なら null（頭文字の丸を出す）
  iconUrl: string | null
}

// 入力の誤り（項目ごと）
export type FieldError = {
  field: string
  message: string
}

// エラーの返事（Problem Details）。detail は画面にそのまま出せる文言、code は見分けるための名前
export type ProblemDetail = {
  status?: number
  detail?: string
  code?: string
  errors?: FieldError[]
}
