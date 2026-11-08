// いいねのボタンの送り方（docs/features/like.md 4.1）。画面の部品から切り離して、テストできるようにしてある
//
// - 押したらすぐ表示を変える（サーバーの返事を待たない）
// - 送信は1つずつ。送っている間に押されたら覚えておき、返事が来たときに「最後に押した状態」とずれていれば、もう一度送る
//   （連打しても、最後に押した状態になる。送る回数も増えすぎない）
// - 失敗したら、サーバーで確かめられた最後の状態に戻す

export type LikeState = { liked: boolean; likeCount: number }

type Handlers = {
  // 送り終えて、表示をサーバーの返事にそろえるとき
  onSettled: (state: LikeState) => void
  // 失敗して、表示を元に戻すとき
  onError: (state: LikeState) => void
}

export type LikeToggler = {
  // 押した。すぐに出す表示を返す
  toggle: () => LikeState
  // 外から新しい状態が届いた（ほかの画面でいいねした、一覧を読み直したなど）。送信中でなければ受け入れる
  sync: (state: LikeState) => boolean
}

export function createLikeToggler(
  initial: LikeState,
  send: (liked: boolean) => Promise<LikeState>,
  handlers: Handlers,
): LikeToggler {
  // サーバーで確かめられた最後の状態
  let confirmed = initial
  // 画面に出している状態（最後に押した状態）
  let shown = initial
  let sending = false

  function flush(): void {
    sending = true
    const target = shown.liked
    send(target)
      .then((result) => {
        confirmed = result
        sending = false
        if (shown.liked !== result.liked) {
          // 送っている間にまた押された。最後に押した状態をもう一度送る
          flush()
          return
        }
        shown = result
        handlers.onSettled(result)
      })
      .catch(() => {
        sending = false
        shown = confirmed
        handlers.onError(confirmed)
      })
  }

  return {
    toggle() {
      shown = { liked: !shown.liked, likeCount: Math.max(0, shown.likeCount + (shown.liked ? -1 : 1)) }
      if (!sending) flush()
      return shown
    },
    sync(state) {
      if (sending) return false
      confirmed = state
      shown = state
      return true
    },
  }
}
