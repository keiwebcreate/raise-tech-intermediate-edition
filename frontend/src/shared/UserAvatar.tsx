import Avatar from '@mui/material/Avatar'

// ユーザーのアイコン。アイコン画像はプロフィールの機能で作るので、今は表示名の頭文字を出す
export function UserAvatar({ displayName, size = 40 }: { displayName: string; size?: number }) {
  const initial = [...displayName.trim()][0] ?? '?'
  return (
    <Avatar
      aria-hidden
      sx={{ width: size, height: size, bgcolor: 'primary.main', fontSize: size * 0.45, fontWeight: 700 }}
    >
      {initial}
    </Avatar>
  )
}
