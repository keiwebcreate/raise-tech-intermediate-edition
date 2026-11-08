// 画面に出す文言のうち、いくつかの場所で使うもの（docs/features/authentication.md 5章）
export const MESSAGES = {
  network: '通信に失敗しました。時間をおいてもう一度お試しください',
  sessionExpired: 'ログインの有効期限が切れました。もう一度ログインしてください',
  required: (label: string) => `${label}を入力してください`,
} as const
