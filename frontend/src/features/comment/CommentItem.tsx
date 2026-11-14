import ReplyIcon from '@mui/icons-material/Reply'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useCallback, useState, type ReactNode } from 'react'
import { formatFullTime, formatRelativeTime } from '../../shared/time.ts'
import { FollowButton } from '../follow/FollowButton.tsx'
import { LikeButton } from '../like/LikeButton.tsx'
import { LikersDialog } from '../like/LikersDialog.tsx'
import type { LikeState } from '../like/likeToggler.ts'
import { AvatarLink, UserNameLink } from '../profile/UserLinks.tsx'
import { getCommentLikers, setCommentLike, type Comment } from './commentApi.ts'
import { showsReplyTo } from './commentState.ts'

type Props = {
  comment: Comment
  onReply: (comment: Comment) => void
  onDelete: (comment: Comment) => void
  // いいね・取り消しを送り終えたとき（一覧の数をそろえる）
  onLikeSettled: (id: number, state: LikeState) => void
  // 返信の入力欄（このコメントに返信しているとき）
  children?: ReactNode
}

// コメント・返信1件（comment.md 4.2・screen-design.md S-04）。
// 右上に、自分のものなら「削除」、他の人のものならフォローボタン（profile.md 4.3）
export function CommentItem({ comment, onReply, onDelete, onLikeSettled, children }: Props) {
  const [likersOpen, setLikersOpen] = useState(false)
  const id = comment.id
  const sendLike = useCallback((liked: boolean) => setCommentLike(id, liked), [id])
  const loadLikers = useCallback((before?: number) => getCommentLikers(id, before), [id])

  return (
    <Box sx={{ display: 'flex', gap: 1.5, py: 1.25 }}>
      <AvatarLink user={comment.author} size={36} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap', minHeight: 32 }}>
          <UserNameLink user={comment.author} />
          <Typography component="span" color="text.secondary" aria-hidden>
            ·
          </Typography>
          <Typography component="span" color="text.secondary" title={formatFullTime(comment.createdAt)}>
            <time dateTime={comment.createdAt}>{formatRelativeTime(comment.createdAt)}</time>
          </Typography>
          {!comment.mine && (
            <Box sx={{ ml: 'auto' }}>
              <FollowButton
                user={comment.author}
                following={comment.author.following}
                followedBy={comment.author.followedBy}
                size="small"
              />
            </Box>
          )}
          {comment.mine && (
            <Button
              size="small"
              color="error"
              onClick={() => onDelete(comment)}
              aria-label="コメントを削除"
              sx={{ ml: 'auto', minWidth: 0 }}
            >
              削除
            </Button>
          )}
        </Box>
        {comment.replyTo && showsReplyTo(comment) && (
          <Typography variant="body2" color="text.secondary">
            返信先：@{comment.replyTo.username}
          </Typography>
        )}
        {/* 改行はそのまま出す。文字は React が自動でエスケープする（要件 N-26） */}
        <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{comment.content}</Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.25, ml: -1 }}>
          <Button
            size="small"
            color="inherit"
            startIcon={<ReplyIcon fontSize="small" />}
            onClick={() => onReply(comment)}
            sx={{ color: 'text.secondary', minWidth: 0 }}
          >
            {/* 「↩ 返信 2」。数は直接の返信だけ（comment.md 4.3）。0 のときは数を出さない */}
            返信{comment.replyCount > 0 && ` ${comment.replyCount}`}
          </Button>
          <LikeButton
            key={id}
            liked={comment.liked}
            likeCount={comment.likeCount}
            send={sendLike}
            onSettled={(state) => onLikeSettled(id, state)}
            onCountClick={() => setLikersOpen(true)}
          />
        </Box>
        {children}
        {likersOpen && <LikersDialog load={loadLikers} onClose={() => setLikersOpen(false)} />}
      </Box>
    </Box>
  )
}
