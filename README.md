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
| フロントエンド | React 19 + TypeScript 7.0 + Vite 8 + Material UI 9（Node.js 24） |
| バックエンド | Java 25 + Spring Boot 4.1 + Gradle |
| データベース | PostgreSQL 18 |
| インフラ | AWS（CloudFront + ALB + EC2 + RDS + S3）+ Terraform。シドニーリージョン |

詳細は [技術スタック](docs/tech-stack.md) を参照。

## プロジェクト構成

```
Intermediate_edition/
├── frontend/          # フロントエンド（React + Vite）※これから作る
├── backend/           # バックエンド（Spring Boot）※これから作る
├── terraform/         # インフラ構成（AWS / Terraform）※これから作る
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
