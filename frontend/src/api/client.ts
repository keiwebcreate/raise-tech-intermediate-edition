// すべての API は、この api（axios）を通して呼ぶ
// - CSRF：axios は Cookie XSRF-TOKEN を読んで、ヘッダー X-XSRF-TOKEN に付ける機能を最初から持つ（同じ住所への通信のとき）。
//   サーバー（Spring Security の csrf.spa()）の名前と同じなので、設定はいらない
// - JWT：HttpOnly の Cookie に入っていて、ブラウザが自動で送る。画面の JavaScript からは読めない（要件 N-27）
// - 401 のときは、リフレッシュトークンでログインを延ばして、もう一度送る（authentication.md 7.3）
// - 失敗したら Problem Details を読んで ApiError を投げる

import { AxiosError, create, type InternalAxiosRequestConfig } from 'axios'
import { MESSAGES } from '../shared/messages.ts'
import type { FieldError, ProblemDetail } from './types.ts'

// API の失敗。画面ではこれを受け取って、文言（detail）を出す
export class ApiError extends Error {
  readonly status: number // HTTP のステータスコード。つながらなかったときは 0
  readonly code: string // "LOGIN_FAILED" など（authentication.md 5章）
  readonly detail: string // 画面にそのまま出せる文言
  readonly errors: FieldError[] // 入力の誤り（項目ごと）

  constructor(status: number, code: string, detail: string, errors: FieldError[] = []) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
    this.errors = errors
  }
}

// API の errors（[{ field, message }]）を { field: message } の形にする。入力欄の下に出すときに使う
export function fieldErrorsOf(error: ApiError): Record<string, string> {
  const result: Record<string, string> = {}
  for (const { field, message } of error.errors) {
    result[field] ??= message
  }
  return result
}

// ログインの延長もできなかった（ログインの期限切れ）ときに呼ぶ処理。
// React の画面の移動はここではできないので、AuthProvider が登録する
let sessionExpiredHandler: (() => void) | null = null

export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

export const api = create({
  headers: { Accept: 'application/json' },
})

// ここに挙げた API の 401 では、ログインの延長をしない
// （ログインの失敗・延長そのものの失敗・ログアウトは、延長しても意味がない）
const NO_REFRESH_URLS = ['/api/auth/signup', '/api/auth/login', '/api/auth/refresh', '/api/auth/logout']

// アクセストークンが切れた（TOKEN_EXPIRED）か、Cookie ごと消えた（UNAUTHENTICATED）ときに延長を試す
const REFRESHABLE_CODES = ['TOKEN_EXPIRED', 'UNAUTHENTICATED']

// もう一度送った通信には印を付けて、2回目の 401 で延長をくり返さないようにする
type RetryableConfig = InternalAxiosRequestConfig & { retried?: boolean }

// 進行中の延長。同時にいくつの通信が 401 になっても、延長（/refresh）は1回にまとめる（authentication.md 7.3・7.4）。
// 同じリフレッシュトークンで2回延長すると、使い回しと見なされてログアウトされるため
let refreshing: Promise<void> | null = null

function refreshOnce(): Promise<void> {
  refreshing ??= api
    .post('/api/auth/refresh')
    .then(() => undefined)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!(error instanceof AxiosError)) throw error
    const config = error.config as RetryableConfig | undefined
    const problem = error.response?.data as ProblemDetail | undefined
    const url = config?.url ?? ''

    if (
      error.response?.status === 401 &&
      config &&
      !config.retried &&
      !NO_REFRESH_URLS.includes(url) &&
      REFRESHABLE_CODES.includes(problem?.code ?? '')
    ) {
      try {
        await refreshOnce()
      } catch {
        // 延長もできなかった。ログイン画面へ移す
        sessionExpiredHandler?.()
        throw toApiError(error)
      }
      config.retried = true
      return api(config)
    }

    // ユーザーがいなくなったときなど、延長の前に「延長できない」と分かる 401
    if (error.response?.status === 401 && problem?.code === 'REFRESH_INVALID' && !NO_REFRESH_URLS.includes(url)) {
      sessionExpiredHandler?.()
    }
    throw toApiError(error)
  },
)

// axios のエラーを ApiError に変える
function toApiError(error: AxiosError): ApiError {
  if (!error.response) {
    // サーバーにつながらなかった
    return new ApiError(0, 'NETWORK_ERROR', MESSAGES.network)
  }
  const problem = error.response.data as ProblemDetail | undefined
  return new ApiError(
    error.response.status,
    problem?.code ?? 'INTERNAL_ERROR',
    problem?.detail ?? MESSAGES.network,
    problem?.errors ?? [],
  )
}
