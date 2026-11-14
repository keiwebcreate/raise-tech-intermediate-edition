import FavoriteIcon from '@mui/icons-material/Favorite'
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import IconButton from '@mui/material/IconButton'
import { useState } from 'react'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import { createLikeToggler, type LikeState } from './likeToggler.ts'

type Props = {
  liked: boolean
  likeCount: number
  // いいね（true）・取り消し（false）をサーバーへ送る。投稿とコメントで使い回せるように、外から渡す
  send: (liked: boolean) => Promise<LikeState>
  // 送り終えたとき（ほかの画面の表示をそろえるため）
  onSettled?: (state: LikeState) => void
  // いいね数を押したとき（いいねした人の一覧 M-02 を開く）
  onCountClick: () => void
}

// いいねのハートと数（like.md 4.1・4.2）。押したらすぐ表示を変え、失敗したら元に戻す。数が 0 のときは数を出さない
export function LikeButton({ liked, likeCount, send, onSettled, onCountClick }: Props) {
  const notify = useNotify()
  const [shown, setShown] = useState<LikeState>({ liked, likeCount })
  // 送り方（連打のときの扱い）は likeToggler.ts。部品を作ったときに1回だけ作る。
  // そのため send・onSettled は最初に受け取ったものをずっと使う。いいねする相手（投稿・コメント）が変わるときは、
  // 使う側で key を変えて部品ごと作り直す
  const [toggler] = useState(() =>
    createLikeToggler({ liked, likeCount }, send, {
      onSettled: (state) => {
        setShown(state)
        onSettled?.(state)
      },
      onError: (state) => {
        setShown(state)
        notify(MESSAGES.likeFailed)
      },
    }),
  )

  // 外から新しい数が届いたら（ほかの画面でいいねした、一覧を読み直したなど）、送信中でなければそろえる。
  // effect を使わず、描くときに「前に受け取った値」と比べる（React がすすめる、props の変化に合わせる書き方）
  const [received, setReceived] = useState<LikeState>({ liked, likeCount })
  if (received.liked !== liked || received.likeCount !== likeCount) {
    setReceived({ liked, likeCount })
    if (toggler.sync({ liked, likeCount })) setShown({ liked, likeCount })
  }

  return (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', color: shown.liked ? 'error.main' : 'text.secondary' }}>
      <IconButton
        size="small"
        color="inherit"
        aria-label={shown.liked ? 'いいねを取り消す' : 'いいねする'}
        aria-pressed={shown.liked}
        onClick={() => setShown(toggler.toggle())}
      >
        {shown.liked ? <FavoriteIcon fontSize="small" /> : <FavoriteBorderIcon fontSize="small" />}
      </IconButton>
      {shown.likeCount > 0 && (
        <ButtonBase
          aria-label={`いいねしたユーザー ${shown.likeCount} 人`}
          onClick={onCountClick}
          // 押せる範囲を 32px 以上にする（指で押しやすく。Lighthouse の目安は 24px 以上）
          sx={{
            minWidth: 32,
            minHeight: 32,
            px: 0.75,
            borderRadius: 1,
            fontSize: '0.875rem',
            '&:hover': { textDecoration: 'underline' },
            // ButtonBase はブラウザのフォーカスの枠を消すので、キーボードで選んだときの印を自分で付ける
            '&.Mui-focusVisible': {
              outline: (theme) => `2px solid ${theme.palette.primary.main}`,
              outlineOffset: 1,
            },
          }}
        >
          {shown.likeCount}
        </ButtonBase>
      )}
    </Box>
  )
}
