import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import HomeIcon from '@mui/icons-material/Home'
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Fab from '@mui/material/Fab'
import IconButton from '@mui/material/IconButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Typography from '@mui/material/Typography'
import { useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router'
import { useAuth } from '../features/auth/AuthContext.ts'
import { usePostActions } from '../features/post/PostActionsContext.ts'
import { UserAvatar } from '../shared/UserAvatar.tsx'

// ログインした後の画面の共通の並び（screen-design.md 3.1・3.2）
// - パソコン（md：1024px 以上）：左にメニュー、真ん中にメイン（600px）
// - タブレット（sm：768〜1023px）：左にアイコンだけのメニュー
// - スマホ（767px 以下）：下にメニュー、右下に丸い「投稿する」
// メニューは、今ある行き先（ホーム・投稿する・自分 → ログアウト）だけ。検索・プロフィールはその機能のときに足す
export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const { openCreate } = usePostActions()
  const { pathname } = useLocation()
  const [accountAnchor, setAccountAnchor] = useState<HTMLElement | null>(null)
  const isHome = pathname === '/'

  const accountMenu = (
    <Menu
      anchorEl={accountAnchor}
      open={accountAnchor !== null}
      onClose={() => setAccountAnchor(null)}
      anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
      transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
    >
      <MenuItem
        onClick={() => {
          setAccountAnchor(null)
          void logout()
        }}
      >
        <ListItemIcon>
          <LogoutIcon fontSize="small" />
        </ListItemIcon>
        ログアウト
      </MenuItem>
    </Menu>
  )

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', minHeight: '100dvh' }}>
      {/* 左のメニュー（タブレット・パソコン） */}
      <Box
        component="nav"
        aria-label="メニュー"
        sx={{
          display: { xs: 'none', sm: 'flex' },
          flexDirection: 'column',
          alignItems: { sm: 'center', md: 'stretch' },
          gap: 1,
          width: { sm: 88, md: 240 },
          flexShrink: 0,
          position: 'sticky',
          top: 0,
          height: '100dvh',
          px: { sm: 1, md: 2 },
          py: 2,
        }}
      >
        <Typography
          component={NavLink}
          to="/"
          sx={{
            color: 'primary.main',
            fontWeight: 700,
            fontSize: { sm: 22, md: 22 },
            textDecoration: 'none',
            px: { md: 1.5 },
            mb: 1,
          }}
        >
          <Box component="span" sx={{ display: { sm: 'none', md: 'inline' } }}>
            RaiseTechtimeline
          </Box>
          <Box component="span" sx={{ display: { sm: 'inline', md: 'none' } }} aria-label="RaiseTechtimeline">
            RT
          </Box>
        </Typography>

        <Button
          component={NavLink}
          to="/"
          aria-current={isHome ? 'page' : undefined}
          startIcon={isHome ? <HomeIcon /> : <HomeOutlinedIcon />}
          sx={{
            justifyContent: { sm: 'center', md: 'flex-start' },
            color: 'text.primary',
            fontSize: 18,
            fontWeight: isHome ? 700 : 400,
            px: { sm: 1.5, md: 2 },
            minWidth: 0,
            '& .MuiButton-startIcon': { mr: { sm: 0, md: 1.5 }, ml: 0 },
          }}
        >
          <Box component="span" sx={{ display: { sm: 'none', md: 'inline' } }}>
            ホーム
          </Box>
        </Button>

        <Button
          variant="contained"
          size="large"
          onClick={openCreate}
          aria-label="投稿する"
          sx={{ display: { sm: 'none', md: 'flex' }, mt: 1 }}
        >
          投稿する
        </Button>
        <IconButton
          onClick={openCreate}
          aria-label="投稿する"
          sx={{
            display: { sm: 'inline-flex', md: 'none' },
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            '&:hover': { bgcolor: 'primary.dark' },
          }}
        >
          <EditIcon />
        </IconButton>

        {user && (
          <ButtonBase
            onClick={(e) => setAccountAnchor(e.currentTarget)}
            // 読み上げの名前は、見えている文字（表示名・@ユーザー名）をそのまま使う（aria-label を付けない）。
            // メニューが開くことは aria-haspopup で伝わる
            aria-haspopup="menu"
            sx={{
              mt: 'auto',
              display: 'flex',
              gap: 1.5,
              justifyContent: 'flex-start',
              p: 1,
              borderRadius: 999,
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <UserAvatar displayName={user.displayName} />
            <Box sx={{ display: { sm: 'none', md: 'block' }, textAlign: 'left', minWidth: 0 }}>
              <Typography sx={{ fontWeight: 700 }} noWrap>
                {user.displayName}
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap>
                @{user.username}
              </Typography>
            </Box>
          </ButtonBase>
        )}
      </Box>

      {/* 真ん中のメイン。スマホでは下のメニューに隠れないよう、下に余白を取る */}
      <Box
        component="main"
        sx={{
          width: '100%',
          maxWidth: 600,
          minWidth: 0,
          bgcolor: 'background.paper',
          borderLeft: { sm: 1 },
          borderRight: { sm: 1 },
          borderColor: { sm: 'divider' },
          pb: { xs: 9, sm: 0 },
        }}
      >
        {children}
      </Box>

      {/* パソコンでは右を空ける（screen-design.md 3.1） */}
      <Box sx={{ display: { xs: 'none', md: 'block' }, width: 240, flexShrink: 0 }} />

      {/* 下のメニュー（スマホ） */}
      <Box
        component="nav"
        aria-label="メニュー"
        sx={{
          display: { xs: 'flex', sm: 'none' },
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          zIndex: 2,
          justifyContent: 'space-around',
          alignItems: 'center',
          height: 56,
          bgcolor: 'background.paper',
          borderTop: 1,
          borderColor: 'divider',
        }}
      >
        <IconButton component={NavLink} to="/" aria-label="ホーム" aria-current={isHome ? 'page' : undefined}>
          {isHome ? <HomeIcon color="primary" /> : <HomeOutlinedIcon />}
        </IconButton>
        {user && (
          <IconButton
            onClick={(e) => setAccountAnchor(e.currentTarget)}
            aria-label="アカウントのメニュー"
            aria-haspopup="menu"
          >
            <UserAvatar displayName={user.displayName} size={28} />
          </IconButton>
        )}
      </Box>

      {/* 右下の丸い「投稿する」（スマホ） */}
      <Fab
        color="primary"
        aria-label="投稿する"
        onClick={openCreate}
        sx={{ display: { xs: 'flex', sm: 'none' }, position: 'fixed', right: 16, bottom: 72, zIndex: 2 }}
      >
        <AddIcon />
      </Fab>

      {accountMenu}
    </Box>
  )
}
