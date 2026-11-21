import SearchIcon from '@mui/icons-material/Search'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import InputAdornment from '@mui/material/InputAdornment'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router'
import { MESSAGES } from '../../shared/messages.ts'
import { usePagedUsers } from '../follow/usePagedUsers.ts'
import { UserListItem } from '../follow/UserListItem.tsx'
import { searchUsers } from './searchApi.ts'
import { isTooLong, queryParamOf, SEARCH_TOO_LONG, searchText } from './searchQuery.ts'

// 入力が止まってから検索するまでの時間（user-search.md 4.1）
const DEBOUNCE_MS = 300

// 読み上げ用の見出し（画面には出さない。ほかの画面と同じく、ページの名前を h1 で伝える）。
// 大きさは '1px' と文字で書く（MUI の sx では、数の 1 は「100%」の意味になる）
const visuallyHidden = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  margin: '-1px',
  padding: 0,
  border: 0,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

// S-08 ユーザー検索（/search?q=…。user-search.md 4.1）。
// 入力が止まって0.3秒たったら検索する（Enter でも。日本語の変換中は検索しない）。検索ボタンは置かない。
// 言葉は URL の ?q= に入れるので、戻る・再読み込みでも同じ結果になる
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const urlQuery = params.get('q') ?? ''
  const [input, setInput] = useState(urlQuery)
  const [composing, setComposing] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // 「戻る」「進む」で URL の言葉が変わったら、検索欄もそろえる（effect の中で setState しないよう、表示のときに比べる）。
  // 自分で URL に入れたときは、入力欄の言葉と同じなので書き換えない（打っている途中の空白を消さない）
  const [syncedQuery, setSyncedQuery] = useState(urlQuery)
  if (urlQuery !== syncedQuery) {
    setSyncedQuery(urlQuery)
    if (urlQuery !== queryParamOf(input)) setInput(urlQuery)
  }

  // URL に言葉を入れる。履歴は増やさない（1文字ごとに「戻る」が増えないように。戻るとひとつ前の画面へ）
  const commit = useCallback(
    (value: string) => {
      const q = queryParamOf(value)
      setParams(q === '' ? {} : { q }, { replace: true })
    },
    [setParams],
  )

  // 入力が止まって0.3秒たったら検索する。変換中は待つ（変換が終わってから0.3秒）
  useEffect(() => {
    if (composing || queryParamOf(input) === urlQuery) return
    const timer = setTimeout(() => commit(input), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [input, composing, urlQuery, commit])

  // 言葉がまだないときは、開いてすぐ打てるようにする（戻ってきたときにスマホのキーボードが出ないよう、言葉があれば何もしない）
  const initialQuery = useRef(urlQuery)
  useEffect(() => {
    if (initialQuery.current === '') inputRef.current?.focus()
  }, [])

  // Enter ですぐ検索する。変換を確定する Enter では検索しない
  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    event.preventDefault()
    commit(input)
  }

  const text = searchText(urlQuery)
  const tooLong = isTooLong(searchText(input))

  return (
    <>
      <Typography component="h1" sx={visuallyHidden}>
        ユーザー検索
      </Typography>
      <Box
        component="search"
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 1,
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
          px: 2,
          py: 1,
        }}
      >
        <TextField
          inputRef={inputRef}
          type="search"
          fullWidth
          size="small"
          placeholder="ユーザーを検索"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          onCompositionStart={() => setComposing(true)}
          onCompositionEnd={() => setComposing(false)}
          error={tooLong}
          helperText={tooLong ? SEARCH_TOO_LONG : undefined}
          slotProps={{
            htmlInput: { 'aria-label': 'ユーザーを検索', enterKeyHint: 'search' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
              sx: { borderRadius: 999 },
            },
          }}
        />
      </Box>

      {text === '' ? (
        <Typography component="output" color="text.secondary" sx={{ display: 'block', p: 4, textAlign: 'center' }}>
          ユーザー名や名前で検索できます
        </Typography>
      ) : isTooLong(text) ? null : (
        // key：言葉が変わったら、一覧を作り直す
        <SearchResults key={text} text={text} />
      )}
    </>
  )
}

function SearchResults({ text }: { text: string }) {
  const load = useCallback((offset?: number) => searchUsers(text, offset), [text])
  const { state, setSentinel, retry } = usePagedUsers(load)

  return (
    <>
      <Box component="ul" aria-label="検索結果" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {state.users.map((user) => (
          <UserListItem key={user.id} user={user} />
        ))}
      </Box>
      {state.status === 'loading' && (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 3 }}>
          <CircularProgress size={24} aria-label="読み込み中" />
        </Box>
      )}
      {state.status === 'error' && (
        <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={retry}>もう一度試す</Button>}>
          {MESSAGES.searchFailed}
        </Alert>
      )}
      {state.status === 'ready' && state.users.length === 0 && (
        <Typography
          component="output"
          color="text.secondary"
          sx={{ display: 'block', p: 4, textAlign: 'center', overflowWrap: 'anywhere' }}
        >
          「{text}」に一致するユーザーは見つかりませんでした
        </Typography>
      )}
      <Box ref={setSentinel} aria-hidden sx={{ height: 1 }} />
    </>
  )
}
