import Avatar from '@mui/material/Avatar'

type Props = {
  displayName: string
  // アイコンの画像の URL。未設定（null）なら、表示名の頭文字を丸の中に出す
  iconUrl?: string | null
  size?: number
}

// ユーザーのアイコン（profile.md 3.1）。
// 名前はすぐ横に文字で出ているので、読み上げでは飛ばす（aria-hidden・alt を空にする）。
// 画像が読めなかったとき（期限切れなど）も、MUI の Avatar が頭文字に切り替える
export function UserAvatar({ displayName, iconUrl, size = 40 }: Props) {
  const initial = [...displayName.trim()][0] ?? '?'
  return (
    <Avatar
      aria-hidden
      src={iconUrl ?? undefined}
      alt=""
      sx={{ width: size, height: size, bgcolor: 'primary.main', fontSize: size * 0.45, fontWeight: 700 }}
    >
      {initial}
    </Avatar>
  )
}
