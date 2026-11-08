# RaiseTechtimeline

X（旧 Twitter）のようなタイムライン形式の、テキスト中心の SNS アプリ。RaiseTech 中級編の課題として、学習目的で作る。

- 複数のユーザーが使う前提で設計する（他の人の投稿にいいね・コメントしたり、フォローしたりできる）
- 主な機能：新規登録・ログイン、プロフィール、フォロー、ユーザー検索、投稿（280文字・画像4枚まで、または動画1本）、タイムライン、コメント（返信も）、いいね（投稿・コメント）

## 技術スタック

| 区分 | 技術 |
|---|---|
| フロントエンド | React 19 ＋ TypeScript 7.0（Vite 8）。ビルドは Node.js 24 |
| バックエンド | Java 25（LTS）＋ Spring Boot 4.1 |
| データベース | PostgreSQL 18 |
| インフラ | AWS（シドニーリージョン）。CloudFront（HTTPS）・ALB・EC2・RDS・S3（画像・動画） |
| 開発ツール | Git・GitHub、Docker |

詳しくは [docs/tech-stack.md](docs/tech-stack.md)。

## ドキュメント

| No | ファイル | 内容 |
|---|---|---|
| 1 | [docs/requirements.md](docs/requirements.md) | 要件定義書（概要・非機能要件・制約・対象外） |
| 2 | [docs/features.md](docs/features.md) | 機能一覧（表）とユースケース |
| 3 | [docs/features/authentication.md](docs/features/authentication.md) | 機能定義書：新規登録・ログイン・ログアウト |
| 4 | [docs/features/timeline.md](docs/features/timeline.md) | 機能定義書：タイムライン（すべて・フォロー中） |
| 5 | [docs/features/post.md](docs/features/post.md) | 機能定義書：投稿（作成・編集・削除・画像・動画） |
| 6 | [docs/features/comment.md](docs/features/comment.md) | 機能定義書：コメント |
| 7 | [docs/features/like.md](docs/features/like.md) | 機能定義書：いいね |
| 8 | [docs/features/profile.md](docs/features/profile.md) | 機能定義書：プロフィール・フォロー |
| 9 | [docs/features/user-search.md](docs/features/user-search.md) | 機能定義書：ユーザー検索 |
| 10 | [docs/screen-design.md](docs/screen-design.md) | 画面設計書（画面一覧・ワイヤーフレーム・画面遷移図） |
| 11 | [docs/database-design.md](docs/database-design.md) | データベース設計書（ER 図・テーブル定義） |
| 12 | [docs/tech-stack.md](docs/tech-stack.md) | 技術スタック |
| 13 | [docs/infrastructure.md](docs/infrastructure.md) | インフラ構成（AWS） |
