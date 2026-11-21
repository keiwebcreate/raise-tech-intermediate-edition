# RaiseTechtimeline - SNSアプリ

X/Twitter 風のテキストベース SNS Web アプリケーション。
RaiseTech 中級編の課題（学習目的）として、フロントエンド・バックエンド・データベースの3層構成で開発する。複数のユーザーが使う前提で設計する。

## 主な機能

- ユーザー登録・ログイン（JWT 認証）
- テキスト投稿（280文字制限）・画像4枚まで、または動画1本の添付（AWS S3）
- 投稿の編集・削除
- タイムライン表示（すべて / フォロー中のタブ切り替え）
- いいね・コメント（数の表示つき）。コメントへの返信・コメントへのいいね
- ユーザープロフィール（アイコン・自己紹介）
- フォロー / フォロワー機能（フォローバック・フォロー数とフォロワー数）
- ユーザー検索（検索画面のほか、コメント・いいねした人の一覧からもフォローできる）

### X/Twitter との差別化

- インプレッション数（表示回数）を表示しない
- リツイート（リポスト）機能を用意しない

## 技術スタック

| レイヤー | 主要技術 |
|---|---|
| フロントエンド | React 19 + TypeScript 7.0 + Vite 8 + Material UI 9 + axios（Node.js 24） |
| バックエンド | Java 25 + Spring Boot 4.1 + Gradle + MyBatis + Flyway（認証は JWT・jjwt） |
| データベース | PostgreSQL 18 |
| インフラ | AWS（CloudFront + ALB + EC2 + RDS + S3）+ Terraform。シドニーリージョン |

詳細は [技術スタック](docs/tech-stack.md) を参照。

## テスト

本来のテスト駆動開発（コードより先にテストを書く）までは していないが、機能を作ったあとに **「TDD 風」** でテストを書いている。

1. 実装は見ずに、**設計書（機能定義書・画面設計書）と、メソッドや部品の「外から見える形」だけ**からテストを書く
2. 失敗（赤）したら、実装と設計書を見比べる。実装が設計書と違えばコードを直し、テストの思い込みならテストを直す
3. 最初から通ったテストは、**わざと実装を壊して失敗することを確かめる**（何も確かめていないテストを残さない）

このやり方で、ログインの延長が 500 になる・検索で記号を取りすぎる・動画の長さを読んでいる間に投稿できる、などの不具合が見つかり、直した（[テスト方針](docs/testing.md) 3.1）。

| 段 | 何を確かめるか | 道具 |
|---|---|---|
| サーバーの単体テスト | Service の判断（本人の確認・重なり・文字数・失敗したときの片付け）。DB・S3 は「にせもの」 | JUnit・Mockito |
| Mapper のテスト | MyBatis の SQL だけ（並び順・数え方・続きの読み方・本人の条件） | `@MybatisTest`・Testcontainers |
| サーバーの結合テスト | API を呼んで DB まで通す（認証・CSRF・エラーの形・N+1 にしないこと〈SQL の回数を数える〉・ログ） | `@SpringBootTest`・MockMvc・Testcontainers |
| 画面のテスト | 関数・フック・部品（押したら表示がどう変わるか・入力の確かめ・エラーの文言。日本語の変換中も） | Vitest・React Testing Library・jsdom |

- テスト用の DB は、H2（メモリの DB）ではなく **Testcontainers で本物の PostgreSQL 18** を動かす（本番の RDS と同じ。テストだけ通って本番で壊れることを防ぐ）
- カバレッジ（テストで通った行の割合）は測るが、目標の数字は決めない（ロジックのある場所に集中する）。2026-11-21 時点：サーバー 行 93%・分かれ道 87%（JaCoCo）、画面の部品・フックは 行 84〜100%（Vitest）
- E2E（ブラウザを動かす通しのテスト）は作らない。画面全体のつながりは、Chrome DevTools MCP を使って手で確かめている
- 2026-11-21 時点でサーバー 300 件・画面 191 件

| 場所 | コマンド |
|---|---|
| サーバー（`backend/`） | `.\gradlew.bat check`（テスト・カバレッジ・Checkstyle・SpotBugs） |
| 画面（`frontend/`） | `npm test`・`npm run test:coverage`・`npm run lint`・`npm run build` |

詳細は [テスト方針](docs/testing.md) を参照。

## ログ・監視・運用

本番（AWS）で起きたことを後から調べられ、障害に気づいて直せるように、ログ・監視・運用・障害対応の決まりを文書にしている。運用と障害対応はデプロイの前のたたき台で、デプロイのときに確かめて直す。

| 何を | どうしているか | 文書 |
|---|---|---|
| ログ | サーバーのログを1行ずつ **JSON（ECS の形）** で出す（構造化ログ）。CloudWatch Logs で項目ごとに絞り込める。パスワード・トークン・メールアドレスは出さない | [ログ設計書](docs/logging.md) |
| リクエストの追跡 | API 1回ごとにリクエスト ID を振り、その間のログすべてと返事のヘッダー `X-Request-Id` に付ける。API ごとのアクセスログ（URL の形・番号・かかった時間・利用者の ID） | [ログ設計書](docs/logging.md) 3章・5章 |
| ログの監視 | ERROR のログが出たら知らせる。WARN（ログインの失敗・盗まれた疑いのトークン）は使うたびに見る | [運用設計書](docs/operations.md) 2章 |
| 運用の監視 | 大事な数か所だけをメールで知らせる（500 番台のエラー・サーバーが生きているか・ERROR のログ・DB の空き容量・費用）。CloudWatch アラーム＋SNS | [運用設計書](docs/operations.md) 2章 |
| 費用 | 使わないときは `terraform destroy` で全部片付け、使うときに作り直す。AWS Budgets で見張る | [運用設計書](docs/operations.md) 4章 |
| バックアップ・デプロイ | RDS の自動バックアップ（7日）と戻し方、1つ前の版に戻す手順、秘密の値の入れ替え | [運用設計書](docs/operations.md) 5〜8章 |
| 障害対応 | 障害のときの流れ、よくある障害9つの「症状 → 見る場所 → 切り分け → 直し方 → 再発の防止」、記録の残し方 | [障害対応書](docs/incident-response.md) |

## プロジェクト構成

```
Intermediate_edition/
├── frontend/          # フロントエンド（React + Vite）
├── backend/           # バックエンド（Spring Boot）
├── compose.yaml       # 開発用の PostgreSQL 18 と S3Mock（S3 の代わり）（Docker）
├── terraform/         # インフラ構成（AWS / Terraform）※これから作る
├── mockup/            # 動くモックアップ（HTML・CSS・JavaScript）。index.html をブラウザで開く
├── docs/              # 設計ドキュメント
│   ├── requirements.md          # 要件定義書
│   ├── features.md              # 機能一覧・ユースケース
│   ├── features/                # 機能定義書
│   │   ├── authentication.md    # 認証機能
│   │   ├── post.md              # 投稿機能
│   │   ├── timeline.md          # タイムライン機能
│   │   ├── comment.md           # コメント機能
│   │   ├── like.md              # いいね機能
│   │   ├── profile.md           # プロフィール・フォロー機能
│   │   └── user-search.md       # ユーザー検索機能
│   ├── screen-design.md         # 画面設計書
│   ├── database-design.md       # データベース設計書
│   ├── tech-stack.md            # 技術スタック
│   ├── testing.md               # テスト方針
│   ├── logging.md               # ログ設計書
│   ├── operations.md            # 運用設計書
│   ├── incident-response.md     # 障害対応書
│   └── infrastructure.md        # インフラ構成
└── README.md
```

## ドキュメント

| No | ファイル | 内容 |
|---|---|---|
| 1 | [docs/requirements.md](docs/requirements.md) | 要件定義書（概要・非機能要件・制約・対象外） |
| 2 | [docs/features.md](docs/features.md) | 機能一覧（表）とユースケース |
| 3 | [docs/features/authentication.md](docs/features/authentication.md) | 機能定義書：新規登録・ログイン・ログアウト |
| 4 | [docs/features/timeline.md](docs/features/timeline.md) | 機能定義書：タイムライン（すべて・フォロー中） |
| 5 | [docs/features/post.md](docs/features/post.md) | 機能定義書：投稿（作成・編集・削除・画像・動画） |
| 6 | [docs/features/comment.md](docs/features/comment.md) | 機能定義書：コメント（返信を含む） |
| 7 | [docs/features/like.md](docs/features/like.md) | 機能定義書：いいね（投稿・コメント） |
| 8 | [docs/features/profile.md](docs/features/profile.md) | 機能定義書：プロフィール・フォロー |
| 9 | [docs/features/user-search.md](docs/features/user-search.md) | 機能定義書：ユーザー検索 |
| 10 | [docs/screen-design.md](docs/screen-design.md) | 画面設計書（画面一覧・ワイヤーフレーム・画面遷移図） |
| 11 | [docs/database-design.md](docs/database-design.md) | データベース設計書（ER 図・テーブル定義） |
| 12 | [docs/tech-stack.md](docs/tech-stack.md) | 技術スタック |
| 13 | [docs/infrastructure.md](docs/infrastructure.md) | インフラ構成（AWS） |
| 14 | [docs/testing.md](docs/testing.md) | テスト方針（単体・結合・画面のテストの分担、TDD 風の進め方、カバレッジ） |
| 15 | [docs/logging.md](docs/logging.md) | ログ設計書（構造化ログ〈JSON・ECS〉、重さの決まり、出すもの・出さないもの、CloudWatch での見方） |
| 16 | [docs/operations.md](docs/operations.md) | 運用設計書（監視と知らせ、費用と片付け、バックアップと戻し方、定期の作業、デプロイと戻し方、秘密の値の入れ替え） |
| 17 | [docs/incident-response.md](docs/incident-response.md) | 障害対応書（障害のときの流れ、よくある障害ごとの調べ方と直し方、記録の残し方） |
