// 画面に出す文言のうち、いくつかの場所で使うもの（docs/features/authentication.md 5章）
export const MESSAGES = {
  network: '通信に失敗しました。時間をおいてもう一度お試しください',
  sessionExpired: 'ログインの有効期限が切れました。もう一度ログインしてください',
  required: (label: string) => `${label}を入力してください`,
  // 投稿（docs/features/post.md 5章・screen-design.md 3.4）
  postFailed: '投稿に失敗しました。時間をおいて再度お試しください',
  posted: '投稿しました',
  postUpdated: '投稿を更新しました',
  postDeleted: '投稿を削除しました',
  postNotFound: 'この投稿は見つかりません',
  loadFailed: '読み込みに失敗しました',
  // いいね（docs/features/like.md 5章）
  likeFailed: 'いいねに失敗しました',
  // コメント（docs/features/comment.md 5章・screen-design.md 3.4）
  commentFailed: 'コメントに失敗しました。時間をおいて再度お試しください',
  commentDeleted: 'コメントを削除しました',
} as const
