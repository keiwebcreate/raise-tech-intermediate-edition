import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Typography from '@mui/material/Typography'
import { useCallback, useState, type MouseEvent } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { formatFullTime, formatRelativeTime } from '../../shared/time.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { getPostLikers, setPostLike } from '../like/likeApi.ts'
import { LikeButton } from '../like/LikeButton.tsx'
import { LikersDialog } from '../like/LikersDialog.tsx'
import { usePostActions } from './PostActionsContext.ts'
import type { Post } from './postApi.ts'
import { PostMediaGrid } from './PostMediaGrid.tsx'

type Props = {
  post: Post
  // list：タイムラインの1件（押すと詳細へ）／detail：投稿の詳細（正確な日時を出す）
  variant?: 'list' | 'detail'
}

// 投稿1件（timeline.md 3.1・screen-design.md S-03・S-04）。
// コメント数は、コメントの機能を作るときに足す
export function PostCard({ post, variant = 'list' }: Props) {
  const navigate = useNavigate()
  const { openEdit, requestDelete, emit } = usePostActions()
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const [likersOpen, setLikersOpen] = useState(false)
  const postId = post.id
  const sendLike = useCallback((liked: boolean) => setPostLike(postId, liked), [postId])
  const loadLikers = useCallback((before?: number) => getPostLikers(postId, before), [postId])
  const href = `/posts/${post.id}`

  // 一覧では、リンク・ボタン以外のところを押すと詳細へ（文字を選んでいるときは移らない）
  function handleClick(event: MouseEvent<HTMLElement>) {
    if (variant !== 'list') return
    // 画像を大きく表示したときなど、投稿の外（ポータル）に出した部品を押したときも、React では投稿まで届くので除く
    if (!event.currentTarget.contains(event.target as Node)) return
    if ((event.target as HTMLElement).closest('a, button, video, [role="menu"]')) return
    if (window.getSelection()?.toString()) return
    navigate(href)
  }

  return (
    <Box
      component="article"
      onClick={handleClick}
      sx={{
        display: 'flex',
        gap: 1.5,
        px: 2,
        py: 1.5,
        borderBottom: 1,
        borderColor: 'divider',
        cursor: variant === 'list' ? 'pointer' : 'auto',
        '&:hover': variant === 'list' ? { bgcolor: 'action.hover' } : undefined,
      }}
    >
      <UserAvatar displayName={post.author.displayName} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap', minHeight: 32 }}>
          <Typography component="span" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
            {post.author.displayName}
          </Typography>
          <Typography component="span" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            @{post.author.username}
          </Typography>
          {variant === 'list' && (
            <>
              <Typography component="span" color="text.secondary" aria-hidden>
                ·
              </Typography>
              <Link
                component={RouterLink}
                to={href}
                color="text.secondary"
                underline="hover"
                title={formatFullTime(post.createdAt)}
              >
                <time dateTime={post.createdAt}>{formatRelativeTime(post.createdAt)}</time>
              </Link>
            </>
          )}
          {post.editedAt && (
            <Typography component="span" variant="body2" color="text.secondary">
              編集済み
            </Typography>
          )}
          {post.mine && (
            <>
              <IconButton
                size="small"
                aria-label="投稿のメニュー"
                aria-haspopup="menu"
                onClick={(e) => setMenuAnchor(e.currentTarget)}
                sx={{ ml: 'auto' }}
              >
                <MoreHorizIcon fontSize="small" />
              </IconButton>
              <Menu anchorEl={menuAnchor} open={menuAnchor !== null} onClose={() => setMenuAnchor(null)}>
                <MenuItem
                  onClick={() => {
                    setMenuAnchor(null)
                    openEdit(post)
                  }}
                >
                  編集
                </MenuItem>
                <MenuItem
                  onClick={() => {
                    setMenuAnchor(null)
                    requestDelete(post)
                  }}
                  sx={{ color: 'error.main' }}
                >
                  削除
                </MenuItem>
              </Menu>
            </>
          )}
        </Box>
        {/* 改行はそのまま出す。文字は React が自動でエスケープする（要件 N-26） */}
        <Typography
          sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: variant === 'detail' ? '1.15rem' : '1rem' }}
        >
          {post.content}
        </Typography>
        <PostMediaGrid media={post.media} />
        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5, ml: -1 }}>
          <LikeButton
            liked={post.liked}
            likeCount={post.likeCount}
            send={sendLike}
            onSettled={({ liked, likeCount }) => emit({ type: 'liked', id: postId, liked, likeCount })}
            onCountClick={() => setLikersOpen(true)}
          />
        </Box>
        {likersOpen && <LikersDialog load={loadLikers} onClose={() => setLikersOpen(false)} />}
        {variant === 'detail' && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
            <time dateTime={post.createdAt}>{formatFullTime(post.createdAt)}</time>
          </Typography>
        )}
      </Box>
    </Box>
  )
}
