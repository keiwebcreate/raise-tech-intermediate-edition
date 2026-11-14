import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import Skeleton from '@mui/material/Skeleton'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { useBlocker, useLocation, useNavigate } from 'react-router'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { ConfirmDialog } from '../../shared/ConfirmDialog.tsx'
import { MESSAGES } from '../../shared/messages.ts'
import { useNotify } from '../../shared/notify.ts'
import { countChars } from '../../shared/text.ts'
import { UserAvatar } from '../../shared/UserAvatar.tsx'
import { useAuth } from '../auth/AuthContext.ts'
import * as profileApi from './profileApi.ts'
import {
  BIO_MAX,
  bioToSend,
  checkBio,
  checkDisplayName,
  checkIconFile,
  DISPLAY_NAME_MAX,
  ICON_ACCEPT,
  PROFILE_MESSAGES,
} from './profileRules.ts'

// プロフィールの「プロフィールを編集」から来たときに渡す印。保存したら、ひとつ前（そのプロフィール）へ戻る
export type ProfileEditState = { fromProfile?: boolean }

type LoadState = { status: 'loading' } | { status: 'ready'; profile: profileApi.Profile } | { status: 'error' }

// S-06 プロフィールの編集（/settings/profile。profile.md 3.2）。
// 読み込んだら入力欄（ProfileForm）を出す
export function ProfileEditPage() {
  const { user } = useAuth()
  const username = user?.username ?? ''
  const [reloadCount, setReloadCount] = useState(0)
  const [loaded, setLoaded] = useState<{ key: number; state: LoadState } | null>(null)
  const state: LoadState = loaded?.key === reloadCount ? loaded.state : { status: 'loading' }

  useEffect(() => {
    let ignore = false
    profileApi
      .getProfile(username)
      .then((profile) => {
        if (!ignore) setLoaded({ key: reloadCount, state: { status: 'ready', profile } })
      })
      .catch(() => {
        if (!ignore) setLoaded({ key: reloadCount, state: { status: 'error' } })
      })
    return () => {
      ignore = true
    }
  }, [username, reloadCount])

  if (state.status === 'ready') return <ProfileForm profile={state.profile} />
  return (
    <>
      <EditHeader />
      {state.status === 'loading' ? (
        <Box sx={{ p: 2 }} aria-label="読み込み中" aria-busy>
          <Skeleton variant="circular" width={88} height={88} />
          <Skeleton variant="rounded" height={56} sx={{ mt: 3 }} />
          <Skeleton variant="rounded" height={96} sx={{ mt: 3 }} />
        </Box>
      ) : (
        <Alert
          severity="error"
          sx={{ m: 2 }}
          action={<Button onClick={() => setReloadCount((n) => n + 1)}>もう一度読み込む</Button>}
        >
          {MESSAGES.loadFailed}
        </Alert>
      )}
    </>
  )
}

// 上の「← プロフィールを編集 [保存する]」
function EditHeader({ onBack, saveButton }: { onBack?: () => void; saveButton?: ReactNode }) {
  const navigate = useNavigate()
  return (
    <Box
      sx={{
        position: 'sticky',
        top: 0,
        zIndex: 1,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        px: 1,
        py: 0.5,
        bgcolor: 'background.paper',
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      <IconButton aria-label="戻る" onClick={onBack ?? (() => navigate(-1))}>
        <ArrowBackIcon />
      </IconButton>
      {/* 320px でも「保存する」と並んで省略されないよう、スマホでは文字を少し小さくする */}
      <Typography
        component="h1"
        variant="h6"
        noWrap
        sx={{ fontWeight: 700, flex: 1, minWidth: 0, fontSize: { xs: '1rem', sm: '1.25rem' } }}
      >
        プロフィールを編集
      </Typography>
      {saveButton}
    </Box>
  )
}

// アイコンの今の状態。key：送るキー（null は「外す」）、url：見せる画像
type IconState = { key: string | null; url: string | null }

function ProfileForm({ profile }: { profile: profileApi.Profile }) {
  const { updateUser } = useAuth()
  const notify = useNotify()
  const navigate = useNavigate()
  const location = useLocation()
  const fromProfile = (location.state as ProfileEditState | null)?.fromProfile === true

  const [displayName, setDisplayName] = useState(profile.displayName)
  const [bio, setBio] = useState(profile.bio ?? '')
  const [icon, setIcon] = useState<IconState>({ key: profile.iconKey, url: profile.iconUrl })
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)
  // 入力の誤り。displayName・bio・iconKey（サーバーの項目名と同じ）
  const [errors, setErrors] = useState<Record<string, string>>({})
  // 一度「保存する」を押したら、入力のたびに誤りを出し直す（押す前は、打っている途中で赤くしない）
  const [submitted, setSubmitted] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const uploadAbortRef = useRef<AbortController | null>(null)
  // 保存できたら、画面を離れるときに確かめない
  const savedRef = useRef(false)

  const dirty = displayName !== profile.displayName || bio !== (profile.bio ?? '') || icon.key !== profile.iconKey

  // 画面を離れるとき（戻る・メニュー・ブラウザの「戻る」）、変えていたら確かめる（profile.md 3.2）
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    return dirty && !savedRef.current && currentLocation.pathname !== nextLocation.pathname
  })

  // 再読み込み・タブを閉じるときは、ブラウザの決まった確認を出す（文言は変えられない）
  useEffect(() => {
    if (!dirty) return
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      if (savedRef.current) return
      event.preventDefault()
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [dirty])

  // 画面を離れたら、送っている途中の画像をやめる
  useEffect(() => () => uploadAbortRef.current?.abort(), [])

  function validate(name: string, text: string): Record<string, string> {
    const result: Record<string, string> = {}
    const nameError = checkDisplayName(name)
    const bioError = checkBio(text)
    if (nameError) result.displayName = nameError
    if (bioError) result.bio = bioError
    return result
  }

  function changeDisplayName(value: string) {
    setDisplayName(value)
    if (submitted) setErrors((prev) => ({ ...prev, displayName: validate(value, bio).displayName ?? '' }))
  }

  function changeBio(value: string) {
    setBio(value)
    if (submitted) setErrors((prev) => ({ ...prev, bio: validate(displayName, value).bio ?? '' }))
  }

  // 画像を選んだら、すぐにサーバーへ送る（正方形にして一時置き場に置く）。「保存する」で正式に差し替わる
  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // 同じファイルをもう一度選べるように
    if (!file) return
    const fileError = checkIconFile(file)
    if (fileError) {
      setErrors((prev) => ({ ...prev, iconKey: fileError }))
      return
    }
    uploadAbortRef.current?.abort()
    const controller = new AbortController()
    uploadAbortRef.current = controller
    setUploading(true)
    setErrors((prev) => ({ ...prev, iconKey: '' }))
    try {
      const uploaded = await profileApi.uploadIcon(file, controller.signal)
      setIcon({ key: uploaded.key, url: uploaded.url })
    } catch (e) {
      if (controller.signal.aborted) return
      const message = e instanceof ApiError ? (fieldErrorsOf(e).file ?? e.detail) : PROFILE_MESSAGES.iconFailed
      setErrors((prev) => ({ ...prev, iconKey: message }))
    } finally {
      if (uploadAbortRef.current === controller) {
        uploadAbortRef.current = null
        setUploading(false)
      }
    }
  }

  function removeIcon() {
    uploadAbortRef.current?.abort()
    setIcon({ key: null, url: null })
    setErrors((prev) => ({ ...prev, iconKey: '' }))
  }

  function goToProfile() {
    if (fromProfile) navigate(-1)
    else navigate(profileApi.profilePath(profile.username), { replace: true })
  }

  async function handleSubmit(event?: FormEvent) {
    event?.preventDefault()
    if (saving || uploading) return
    setSubmitted(true)
    const found = validate(displayName, bio)
    setErrors((prev) => ({ ...found, iconKey: prev.iconKey ?? '' }))
    if (Object.keys(found).length > 0) return
    setSaving(true)
    try {
      const saved = await profileApi.updateProfile({
        displayName: displayName.trim(),
        bio: bioToSend(bio),
        iconKey: icon.key,
      })
      savedRef.current = true
      updateUser({ displayName: saved.displayName, iconUrl: saved.iconUrl })
      notify(MESSAGES.profileUpdated)
      goToProfile()
    } catch (e) {
      if (e instanceof ApiError && e.errors.length > 0) {
        setErrors(fieldErrorsOf(e))
      } else {
        notify(e instanceof ApiError ? e.detail : MESSAGES.network)
      }
      setSaving(false)
    }
  }

  const nameCount = countChars(displayName.trim())
  const bioCount = countChars(bio.trim())

  return (
    <>
      <EditHeader
        onBack={goToProfile}
        saveButton={
          <Button
            variant="contained"
            type="submit"
            form="profile-form"
            disabled={saving || uploading}
            sx={{ flexShrink: 0 }}
          >
            保存する
          </Button>
        }
      />
      <Box component="form" id="profile-form" noValidate onSubmit={handleSubmit} sx={{ p: 2 }}>
        {/* アイコン：画像を選ぶ・外す */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <Box sx={{ position: 'relative' }}>
            <UserAvatar displayName={displayName || profile.displayName} iconUrl={icon.url} size={88} />
            {uploading && (
              <Box
                sx={{
                  position: 'absolute',
                  inset: 0,
                  display: 'grid',
                  placeItems: 'center',
                  borderRadius: '50%',
                  bgcolor: 'rgba(0, 0, 0, 0.4)',
                }}
              >
                <CircularProgress size={28} sx={{ color: 'common.white' }} aria-label="画像を送信中" />
              </Box>
            )}
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button variant="outlined" onClick={() => fileInputRef.current?.click()} disabled={saving}>
              画像を選ぶ
            </Button>
            <Button color="inherit" onClick={removeIcon} disabled={saving || (icon.key === null && !uploading)}>
              画像を外す
            </Button>
          </Box>
          <input
            ref={fileInputRef}
            type="file"
            accept={ICON_ACCEPT}
            hidden
            aria-label="アイコンの画像"
            onChange={(e) => void handleFile(e)}
          />
        </Box>
        {errors.iconKey && (
          <Typography role="alert" variant="body2" color="error" sx={{ mt: 1 }}>
            {errors.iconKey}
          </Typography>
        )}

        <TextField
          label="表示名"
          value={displayName}
          onChange={(e) => changeDisplayName(e.target.value)}
          error={Boolean(errors.displayName)}
          helperText={<FieldHelp error={errors.displayName} count={nameCount} max={DISPLAY_NAME_MAX} />}
          required
          fullWidth
          autoComplete="nickname"
          sx={{ mt: 3 }}
        />
        <TextField
          label="自己紹介"
          value={bio}
          onChange={(e) => changeBio(e.target.value)}
          error={Boolean(errors.bio)}
          helperText={<FieldHelp error={errors.bio} count={bioCount} max={BIO_MAX} />}
          multiline
          minRows={3}
          fullWidth
          sx={{ mt: 3 }}
        />
      </Box>

      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="編集内容を破棄しますか？"
        confirmLabel="破棄する"
        danger
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />
    </>
  )
}

// 入力欄の下の「誤りの文言」と「12/50」。超えたら数を赤くする
function FieldHelp({ error, count, max }: { error?: string; count: number; max: number }) {
  return (
    <Box component="span" sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
      <Box component="span">{error}</Box>
      <Box
        component="span"
        sx={{ flexShrink: 0, fontVariantNumeric: 'tabular-nums', color: count > max ? 'error.main' : 'text.secondary' }}
      >
        {count}/{max}
      </Box>
    </Box>
  )
}
