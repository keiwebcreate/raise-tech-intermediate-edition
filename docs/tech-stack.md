# 技術スタック：RaiseTechtimeline

| 項目 | 内容 |
|---|---|
| 版 | 0.2 |
| 作成日 | 2026-11-08 |
| 状態 | たたき台（ユーザーの確認待ち） |

---

## 1. 方針

- 講義の指定：React（TypeScript）＋ Spring Boot ＋ PostgreSQL。インフラは AWS
- バージョンは、講義の「タスクマネジメント（前のアプリ）と同じものを使う」に合わせて、**トレロ風アプリ（`../beginner_version`）と同じ**にする
- Node.js だけは、AWS でのデプロイの都合で **24** に決める（ユーザー指定）
- 講義の README にある Material UI と Terraform も使う（ユーザーの判断）。バージョンは最新にする

---

## 2. 一覧

### 2.1 画面（フロントエンド）

| もの | バージョン | 説明 |
|---|---|---|
| React | 19（トレロ風は 19.3.1） | 画面を作るライブラリ |
| TypeScript | 7.0（トレロ風は 7.0.2） | 型（データの形）を確かめられる JavaScript |
| Vite | 8（トレロ風は 8.3.5） | 開発用のサーバーと、本番用のファイルを作る道具 |
| Material UI | **9**（2026-11-08 に npm で最新を確認。9.5.0） | ボタン・入力欄・モーダル・タブなどの部品集（講義と同じ道具。講義は 7 だが、ユーザーの判断で最新にする） |
| React Router | 8（トレロ風は 8.5.1） | URL ごとに画面を切り替える |
| Node.js | **24** | Vite・npm を動かす。画面のビルド（本番用のファイル作り）に使う |
| Vitest | 5 | 画面のテスト |

### 2.2 サーバー（バックエンド）

| もの | バージョン | 説明 |
|---|---|---|
| Java | **25**（最新の LTS＝長く使える版） | |
| Spring Boot | **4.1.2**（2026-11-08 に公式サイトで最新を確認。トレロ風と同じ） | Java で Web の API を作る土台 |
| Gradle | 9（トレロ風は 9.8.2） | ビルドの道具 |
| Spring Security | Spring Boot が決める | ログイン・JWT の確認 |
| Spring Data JPA | Spring Boot が決める | DB の読み書き |
| Flyway | Spring Boot が決める | テーブルの作成・変更 |
| AWS SDK for Java | 2 系の最新 | S3 への画像の保存 |
| JUnit・Testcontainers | Spring Boot が決める | サーバーのテスト（テスト用の PostgreSQL を Docker で動かす） |

### 2.3 データベース

| もの | バージョン | 説明 |
|---|---|---|
| PostgreSQL | **18** | 開発は Docker の `postgres:18`、本番は RDS の PostgreSQL 18 |

### 2.4 インフラ・開発の道具

| もの | 説明 |
|---|---|
| AWS | シドニーリージョン（ap-southeast-2）。CloudFront（HTTPS）・ALB・EC2・RDS・S3（[infrastructure.md](infrastructure.md)） |
| Terraform | **1.17**（2026-11-08 に最新を確認。1.17.1）。AWS の部品をコードで書いて作る道具（講義と同じ）。AWS 用のプロバイダーは 6 系（6.72.0）。ファイルは `terraform/` |
| Nginx | EC2 の中で、画面のファイルを配り、`/api` を Spring Boot へ転送する |
| Docker | 開発中の PostgreSQL と、テスト |
| Git・GitHub | ソース管理。GitHub Actions で自動テスト |

---

## 3. 認証の方式

| 項目 | 内容 |
|---|---|
| 方式 | JWT（講義の方針） |
| 保存場所 | HttpOnly・Secure・SameSite の Cookie（画面の JavaScript から読めない） |
| 細かい点 | 期限・期限切れの扱い・CSRF の対策は詳細設計で決める |

トレロ風アプリはセッション方式（サーバーにログインの状態を持つ）だったので、今回は違う方式を学ぶことになる。

---

## 4. 確認元

| もの | 確認したところ |
|---|---|
| Spring Boot | https://spring.io/projects/spring-boot （2026-11-08。4.1.2） |
| Material UI | `npm view @mui/material`（2026-11-08。9.5.0。React 19 に対応） |
| Terraform | HashiCorp の更新確認 API（2026-11-08。1.17.1）、AWS プロバイダーは Terraform Registry（6.72.0） |
| そのほか | トレロ風アプリの `docs/detailed-design.md` 5.1・`frontend/package.json`（2026-10-31 時点の最新） |

環境構築のときに、もう一度その時点の最新を確かめる（講義の指定があればそちらを優先）。

## 5. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-11-08 | たたき台を作成 |
| 0.2 | 2026-11-08 | 講義の README に合わせ、Material UI 9 と Terraform 1.17 を追加（ユーザーの判断）。HTTPS は CloudFront |
