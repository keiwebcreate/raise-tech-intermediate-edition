# ログ設計書：RaiseTechtimeline

| 項目 | 内容 |
|---|---|
| 版 | 0.1（2026-11-21） |
| 関係する要件 | N-31（内部情報の漏えい）・N-42（エラーをログに残す・パスワードやトークンは残さない）・[infrastructure.md](infrastructure.md)（CloudWatch Logs） |
| 状態 | たたき台（ユーザーの確認待ち） |

## 1. 目的

- 障害のときに「いつ・誰の・どの API で・何が起きたか」を後から調べられるようにする（N-42）
- 1行ずつ **JSON**（構造化ログ）にして、本番の CloudWatch Logs で項目ごとに絞り込めるようにする
- パスワード・トークン・メールアドレス・本文などは出さない（6章）

講義のスクショはない（ユーザーの判断、2026-11-21）。Spring Boot 4.1 に最初からある構造化ログの仕組みを使う。

## 2. 出し方

| 項目 | 決まり |
|---|---|
| 形 | **ECS**（Elastic Common Schema。広く使われる決まった項目名）。`logging.structured.format.console: ecs`（ユーザーの決定） |
| 出す先 | 標準出力（コンソール）だけ。ファイルには書かない |
| 本番 | systemd が受け取り、CloudWatch Logs へ送る（14日保存。送り方は運用設計書で決める） |
| 開発・テスト | **開発中・テストも JSON**（本番と同じ形。ユーザーの決定） |
| 環境の名前 | `service.environment` に `production`（本番）・`development`（開発）・`test`（テスト） |

## 3. 1行の中身

Spring Boot が ECS の形で入れる項目に、このアプリの項目を足す。

| 項目 | 例 | 中身 |
|---|---|---|
| `@timestamp` | `2026-11-21T10:15:00.067Z` | 日時（UTC） |
| `log.level` | `INFO` | 重さ（4章） |
| `log.logger` | `io.github…common.logging.RequestLoggingFilter` | 出したクラス |
| `message` | `GET /api/posts/{id} 200 35ms` | 人が読む文（日本語でよい） |
| `service.name`・`service.environment` | `raisetechtimeline`・`production` | アプリの名前と環境 |
| `error.type`・`error.message`・`error.stack_trace` | | 例外があるときだけ |
| `requestId` | `7f3c…`（ランダムな文字） | **リクエストの ID**。1回の API の間に出たログすべてに付く。返事のヘッダー `X-Request-Id` にも入れる |
| `userId` | `12` | ログイン中の人の ID（ログインしていなければ付かない）。メールアドレスやユーザー名ではなく ID |
| `method`・`path`・`status`・`durationMs` | `GET`・`/api/posts/{id}`・`200`・`35` | アクセスログだけ（5章） |
| `clientIp` | `10.0.1.2` | ログイン・回数制限のログだけ（同じ接続元からの失敗を追うため） |

- `path` は URL の**形**（`/api/posts/{id}`）。どの API かで数えられるようにするため。形が分からないとき（ない URL・ログインで断られたとき）は、実際の URL をそのまま入れる
- URL の `?` から後ろ（検索の言葉など）は出さない
- `requestId`・`userId` は MDC（その間に出すログすべてに付く「付箋」）に貼る。リクエストが終わったら、API 以外の住所でも**必ず外す**（サーバーは処理の担当〈スレッド〉を使い回すので、外さないと次の別の人のログに付いてしまう）

## 4. 重さ（ログのレベル）の決まり

| 重さ | 使うとき | 見る人がすること |
|---|---|---|
| ERROR | アプリが想定していない失敗（500 になったもの）。原因を調べるため、例外の中身（スタックトレース）も出す | すぐ調べる |
| WARN | 想定はしているが、気にしておきたいこと（盗まれたかもしれないトークン・ログインの回数制限・S3 のファイルが消せない など） | 数が増えていないかを見る |
| INFO | ふつうの出来事（アクセスログ・起動・定期の片付け） | 必要なときに読む |
| DEBUG | 開発のときだけ見る細かいこと（MyBatis の SQL） | 本番では出さない |

- 400・401・403・404・409 など、利用者の入力や状態で起きるエラーは ERROR にしない（アクセスログの `status` で分かる）
- 本番の既定の重さは INFO。このアプリの DEBUG は開発（`application-dev.yml`）だけで出す

## 5. 何を出すか

| 出来事 | 重さ | 出すクラス | 文と項目 |
|---|---|---|---|
| API が1回呼ばれた（アクセスログ） | INFO | `RequestLoggingFilter` | 「`GET /api/posts/{id} 200 35ms`」・`method`・`path`・`status`・`durationMs`（＋ `requestId`・`userId`） |
| 想定していない失敗（500） | ERROR | `ApiExceptionHandler` | 「予期しないエラー」と例外 |
| ログインの失敗 | WARN | `AuthService` | 「ログインに失敗しました」・`clientIp`（**メールアドレスは出さない**） |
| ログインの回数制限で断った | WARN | `AuthService` | 「ログインの失敗が多すぎるため断りました」・`clientIp` |
| 使用済みのリフレッシュトークンが使われた（盗まれた疑い） | WARN | `RefreshTokenService` | 「…トークン N 件を取り消しました」・`userId` |
| S3 のファイル・一時ファイルを消せなかった | WARN | `MediaStorage`・`MediaService` | S3 のキー・一時ファイルの場所と例外 |
| 期限切れのリフレッシュトークンの片付け（1日1回） | INFO | `RefreshTokenCleanupJob` | 消した件数 |
| SQL（開発だけ） | DEBUG | MyBatis の Mapper | SQL と値 |

- アクセスログは `/api/` の住所だけ出す（画面のファイルは Nginx が配るので、Spring Boot には来ない）

## 6. 出さないもの

| 出さないもの | 理由 |
|---|---|
| パスワード・JWT・リフレッシュトークン・CSRF トークン・Cookie | 盗まれるとなりすませる（N-42） |
| メールアドレス | 個人を特定できる（CLAUDE.md） |
| 投稿・コメントの本文、検索の言葉（URL の `?` 以降） | 利用者の書いた中身。調べるのに要らない |
| 送られた JSON（リクエストの本文）・返事の本文 | 上のものが入る |
| DB の接続先のパスワード・JWT の鍵などの設定の値 | 秘密の値 |

- 例外の文（`error.message`）に上のものが入らないよう、自分で投げる例外の文に入れない
- スタックトレースはログにだけ出し、API の返事には出さない（N-31。`ApiExceptionHandler` が決まった文言だけ返す）

## 7. 見方

### 7.1 開発中

ターミナルに JSON が1行ずつ出る。項目だけ読みたいときは、PowerShell で次のようにする。

```powershell
# message だけ読む（bootRun の出力をファイルに残したとき）
Get-Content app.log | ForEach-Object { ($_ | ConvertFrom-Json).message }
```

### 7.2 本番（CloudWatch Logs Insights）

CloudWatch Logs Insights は JSON の項目をそのまま使える。

```
# 500 になったエラー（新しい順）
fields @timestamp, message, requestId, userId, `error.type`
| filter `log.level` = "ERROR"
| sort @timestamp desc

# 1つのリクエストで起きたことをすべて（画面のエラーの報告に X-Request-Id があるとき）
fields @timestamp, `log.level`, message
| filter requestId = "7f3c..."
| sort @timestamp asc

# 遅い API（1秒以上）
fields @timestamp, method, path, status, durationMs
| filter durationMs >= 1000
| sort durationMs desc

# API ごとの回数と、平均の時間
stats count(*), avg(durationMs) by method, path
```

監視（ERROR の数で知らせるなど）は、運用設計書で決める。

## 8. この文書で新しく決めたこと（確認をお願いします）

- アクセスログの項目名は ECS の長い名前（`http.request.method` など）ではなく、短い名前（`method`・`path`・`status`・`durationMs`）にする。読みやすさのため
- 返事のヘッダー `X-Request-Id` にリクエストの ID を入れる（画面でエラーが出たとき、ログを探す手がかりにする）
- ログインの失敗と回数制限は WARN にし、接続元の IP アドレスを入れる（メールアドレスは入れない）。本番では CloudFront・ALB・Nginx を通るので、本当の接続元の取り方はデプロイのときに見直す（authentication.md 9章と同じ）
- 400・401・403・404・409 は ERROR にしない

## 9. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-11-21 | たたき台を作成（イシュー #55） |
