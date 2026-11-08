// API の返事の形（docs/features/authentication.md 8章）

// ログインしている本人
export type UserResponse = {
  id: number
  email: string
  username: string
  displayName: string
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
