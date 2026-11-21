# テスト方針：RaiseTechtimeline

| 項目 | 内容 |
|---|---|
| 版 | 0.4（2026-11-21） |
| 関係する要件 | N-43（自動テスト）・[tech-stack.md](tech-stack.md) |

## 1. テストの種類と分担

講義の「テスト」の回の4段に合わせる。E2E は講義でも「今回はやらなくてよい」なので作らない。

| 段 | 何を確かめるか | 道具 | 場所 | 状態 |
|---|---|---|---|---|
| ① 単体テスト（サーバー） | Service・ルールのクラスの**判断**（本人の確認・重なり・文字数・状態の変わり方・失敗したときの片付け）。DB・S3 などは Mockito の「にせもの」にする | JUnit・Mockito | `backend/src/test/.../*ServiceTest.java` など | できた（#49） |
| ② Mapper（SQL）のテスト | MyBatis の SQL だけ（並び順・数え方・続きの読み方・重なり・本人の条件・削除のつながり・DB の決まり） | `@MybatisTest`・Testcontainers | `backend/src/test/.../*MapperTest.java` | できた（#51） |
| ③ 結合テスト（サーバー） | API を呼んで、Controller → Service → Mapper → DB まで通す。認証・CSRF・エラーの形・N+1 にしないこと | `@SpringBootTest`・`@AutoConfigureMockMvc`・Testcontainers | `backend/src/test/.../*ApiTest.java` | できた（各機能の PR） |
| ④ 画面の単体テスト | 画面のロジック（文字数・日時・状態の変わり方）、フック・部品（押したら表示がどう変わるか・入力の確かめ・エラーの文言・画面の移り先） | Vitest・React Testing Library・jsdom | `frontend/src/**/*.test.ts(x)` | できた（関数は各機能の PR、フック・部品は #53） |

- 単体テストは速く、結合テストでは起こしにくい道（同時に押された・S3 が失敗した・使用済みのトークンが使われた など）を「にせもの」で起こして確かめる
- 結合テストは本物に近い。単体テストで「にせもの」にした部分（SQL・Spring Security・JSON の形）が正しくつながっているかを確かめる
- 同じことを両方で確かめてもよい。ただし、単体テストは**判断**、結合テストは**つながり**を中心にする

## 2. テスト用の DB

- **Testcontainers の PostgreSQL 18**（本番の RDS と同じ）を使う。H2（メモリの DB）は使わない（2026-11-21、ユーザーの決定）
  - 講義の先生の心配「本番と動きが違う DB だと、テストだけ通って本番で壊れる」が起きない
  - このアプリは PostgreSQL だけの SQL（`ON CONFLICT`・`normalize()`・部分索引など）を使っている
- テストのたびに Docker で起動し、テストのあいだは1つの DB を共有する。そのため、テストは**ほかのテストのデータがあっても通る**ように書く
  - 例：検索のテストはランダムな目印を名前に入れる。「すべて」タブの並びのテストは、自分の投稿の日時をはっきり決める（終わったら戻す）。新しい投稿の数は「増えた分」で確かめる
  - 理由：投稿の日時は DB の時計（`now()`）で決まる。テスト中に Docker の時計が巻き戻り、先に流れたほかのテストの投稿が「新しい」と扱われて、ときどき失敗した（2026-11-21）

### 2.1 Mapper のテストの作り（#51）

- `@MybatisTest`＋`@AutoConfigureTestDatabase(replace = NONE)`＋`MapperTestConfiguration`（PostgreSQL だけを動かす。S3 は使わない）。テーブルは Flyway で本番と同じものを作る
- **1つのテストが1つのトランザクション**で動き、終わると取り消される。そのため、ほかのテストのデータは混ざらない
- トランザクションの中の `now()` はすべて同じ時刻になる。並び順を確かめるときは、日時をはっきり決めて入れる（`TestData`）
- PostgreSQL は、トランザクションの中で一度エラーが起きると、そのあとの SQL をすべて断る。DB の決まり（CHECK・一意制約）の違反は、**1つのテストで1つだけ**確かめる

### 2.2 画面のテストの作り（#53）

- ブラウザの代わりは **jsdom**（ユーザーの決定。happy-dom より本物に近い）。`vite.config.ts` の `test.environment`
- `src/test/setup.ts`：どのテストの前にも読む。画面の確かめ方（jest-dom の `toBeInTheDocument()` など）を足し、テストごとに画面を片付ける
- `src/test/render.tsx` の `renderWithApp(部品, { user, url, follow })`：本物のアプリ（`App.tsx`）と同じ包み（テーマ・ルーター・ログイン中の本人・お知らせ・投稿の操作）で描く。包みの中身は「にせもの」で、`auth.loggedIn`・`notify`・`postActions.openEdit` などが呼ばれたかを確かめられる。`follow: true` なら本物の `FollowProvider` で包む。今の URL は `getByTestId('location')`
- サーバーは呼ばない。API の関数（`followApi.ts` など）は `vi.mock` でにせものにして、返事（成功・失敗・返事を待たせる）をテストごとに決める
- jsdom にないもの：「一番下が見えた」は `src/test/intersection.ts` の `FakeIntersectionObserver.intersect()` で起こす。動画の長さは、テストの中で `<video>` の `duration` と読み終えた知らせ（`loadedmetadata`）を決める
- 時計を進めたいとき（60秒ごとの確かめ）は `vi.useFakeTimers()`
- 部品は**使う人から見える形**で探す：役割と読み上げの名前（`getByRole('button', { name: 'いいねする' })`）、入力欄の名前（`getByLabelText`）、画面の文言（`getByText`）。中の作り（クラス名など）には頼らない
- 日本語の変換中は `compositionStart`・`compositionEnd` の合図や `isComposing: true` のキーで起こせる（これまで「手で試すしかない」としていたもの）

| 対象 | テストのファイル |
|---|---|
| フック | `usePagedUsers`（20件ずつ・続き・重なり・失敗）・`useNewPostCount`（60秒ごと・タブが隠れている間・タブの切り替え）・`useMediaDraft`（画像・動画の選び方と送信） |
| 部品 | `FollowButton`（＋ `FollowProvider`）・`LikeButton`・`ConfirmDialog`・`RequireAuth`／`GuestOnly`・`LoginPage`・`SignupPage`・`CommentComposer`・`PostComposer`・`PostCard`・`SearchPage` |

- 画面全体（タイムライン・投稿の詳細・プロフィール・メニュー）はテストしていない。中の部品とフックをテストし、つながりは手での確認（Chrome DevTools MCP）で見ている。E2E は作らない（1章）

### 2.3 ログのテスト（#55）

- `common/logging/LoggingApiTest`：API を呼び、Spring Boot の `OutputCaptureExtension` で標準出力を受け取って、1行ずつ JSON として読む（[logging.md](logging.md)）
- ほかのテストのログも混ざるので、返事のヘッダー `X-Request-Id`（リクエストの ID）やランダムな目印で、自分の行を探す
- 「出してはいけない値が出ていない」は、**JSON の行だけ**で確かめる。MockMvc はテストが失敗すると送った中身（パスワードを含む）を表示するので、出力すべてで見ると、ほかのテストの失敗につられて落ちる

## 3. 進め方（TDD 風。2026-11-21、ユーザーの決定）

機能はすでに作ってあるので、本来の TDD（コードより先にテスト）を次のように当てはめる。

1. **実装を見ずに、設計書（`docs/features/*.md`）とメソッドの名前・引数（`javap` の目次）だけからテストを書く**
2. 流す。**赤（失敗）**なら、実装と設計書を見比べて判断する
   - 実装が設計書と違う → 不具合。コードを直して**緑**にする
   - テストの思い込み（設計書に書いていないことを決めつけていた）→ テストを直す。設計書があいまいなら設計書も直す
3. 動きを変えずにコードを整える（**リファクタリング**）。テストが緑のままであることを確かめる
4. 最初から緑だったテストは、一度だけ**わざとコードを壊して**赤になるかを見る（何も確かめていないテストでないか）

### 3.1 #49 で見つかったこと

| 見つけたテスト | 内容 | 直したもの |
|---|---|---|
| `AuthServiceTest` | ログインの延長のとき、本人が見つからないと 500 になっていた（設計書は 401 `REFRESH_INVALID`） | コード |
| `UserSearchQueryTest` | 検索の「@@taro」で @ を2つとも取っていた（設計書は「先頭の @」。画面は1つだけ取る） | コード |
| `PostServiceTest` | 画像・動画の確かめを、投稿の行を入れたあとにしていた（トランザクションで取り消されるので不具合ではない。設計書の順に合わせた） | コード（リファクタリング） |
| `PostServiceTest` | 画像の並び順を 0 から数えると思い込んでいた（設計書は 1〜4） | テスト |
| `UserSearchQueryTest`（品質チェックのとき） | NBSP（改行しない空白）だけの検索の言葉が、そろえると空になり、全員が見つかっていた（設計書は「検索する言葉を入力してください」） | コード（先にテストで赤を確かめてから直した） |
| `PostMapperTest`（#51） | DB の決まりの違反を1つのテストで2つ確かめようとして、2つ目がトランザクションの中断で断られた（PostgreSQL の決まり） | テスト（2つに分けた） |
| `LoginRateLimiterTest` | 待つ秒数を「900 以下」と決めつけていた（実装は端数を切り上げて 901） | テスト（「言われた秒数を待てば試せる」という動きを確かめる形に） |
| `PostComposer`・`useMediaDraft`（#53） | 動画を選んでから長さを読み終えるまでの間、何も並ばず「投稿する」「画像を選ぶ」を押せた（その間に動画なしで投稿したり、画像と混ぜたりできた。post.md 4.1） | コード（選んだらすぐ「確かめ中」（`checking`。画面は「動画を確かめています」）で並べ、読み終えてから送る。ユーザーの決定） |
| `useMediaDraft`（#53） | 動画を選んだ直後に「投稿できる」になると思い込み、待ち方を誤った | テスト |
| `LoginPage`（#53） | 戻り先がないときは何も渡さないと思い込んでいた（実装は `/` を渡す。どちらもタイムラインへ） | テスト |
| `PostCard`・`SearchPage`（#53） | 名前のリンクは「佐藤 花子 @hanako」の1つ、検索欄の役割は `searchbox`（`type="search"`）だった | テスト（探し方） |
| `RouteGuards`（#53） | テスト用のルーターがどの URL でも同じ画面を描いたため、「ログイン画面へ移す」が終わらずメモリが尽きた | テスト（本物と同じく `/login` を別の画面に） |

## 4. カバレッジ

- サーバーは **JaCoCo**。`.\gradlew.bat test` のあとに `backend/build/reports/jacoco/test/html/index.html` に報告が出る
- **目標の数字は決めない**（2026-11-21、ユーザーの決定。講義：100% を目指さず、ロジックがある場所に集中する）。「テストしていない分かれ道」を見つけるために使う
- #49 の前後：行 91.4% → 93.1%、分かれ道 84.0% → 87.3%（Service・ルールのクラスは、ほとんどの分かれ道を通る）
- 画面は **Vitest のカバレッジ（v8）**。`npm run test:coverage` のあとに `frontend/coverage/index.html` に報告が出る（Git には入れない）
- #53 のあと：画面全体で行 48.1%・分かれ道 40.0%。テストした部品・フックは行 84〜100%（`LoginPage` 100%・`usePagedUsers` 100%・`useMediaDraft` 97.9%・`SearchPage` 94.1% など）。0% なのは画面全体の部品（`TimelinePage`・`ProfilePage`・`AppLayout` など。2.2）

## 5. コマンド

| 場所 | コマンド | 中身 |
|---|---|---|
| サーバー | `.\gradlew.bat check`（`backend`） | 単体・結合テスト・カバレッジの報告・Checkstyle・SpotBugs |
| サーバー（一部だけ） | `.\gradlew.bat test --tests "*.auth.AuthServiceTest"` | 1つのテストのクラスだけ |
| 画面 | `npm test`（`frontend`） | Vitest（関数・フック・部品） |
| 画面（カバレッジ） | `npm run test:coverage`（`frontend`） | Vitest とカバレッジの報告 |
| 画面（一部だけ） | `npx vitest run src/features/search` | そのフォルダ（ファイル）のテストだけ |

## 6. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-11-21 | 単体テスト（#49）の実装に合わせて作成。テストの分担・テスト用の DB・TDD 風の進め方・カバレッジ |
| 0.2 | 2026-11-21 | Mapper のテスト（#51）を追加。作り（2.1）と見つかったこと（3.1） |
| 0.3 | 2026-11-21 | 画面のフック・部品のテスト（#53）を追加。作り（2.2）・見つかったこと（3.1）・画面のカバレッジ（4章）・コマンド（5章） |
| 0.4 | 2026-11-21 | ログのテスト（#55）を追加（2.3） |
