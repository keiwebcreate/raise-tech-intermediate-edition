# 技術スタック：RaiseTechtimeline

| 項目 | 内容 |
|---|---|
| 版 | 0.4 |
| 作成日 | 2026-11-08 |
| 状態 | たたき台（ユーザーの確認待ち） |

---

## 1. 方針

- 講義の指定：React（TypeScript）＋ Spring Boot ＋ PostgreSQL。インフラは AWS
- バージョンは、講義の「タスクマネジメント（前のアプリ）と同じものを使う」に合わせて、**トレロ風アプリ（`../beginner_version`）と同じ**にする。トレロ風アプリにないものは、環境構築のときの最新にする
- Node.js だけは、AWS でのデプロイの都合で **24** に決める（ユーザー指定）
- 講義の README にある Material UI と Terraform も使う（ユーザーの判断）。バージョンは最新にする

---

## 2. 一覧

### 2.1 画面（フロントエンド）

| もの | バージョン | 説明 |
|---|---|---|
| React | 19.3.1 | 画面を作るライブラリ |
| TypeScript | 7.0.2 | 型（データの形）を確かめられる JavaScript |
| Vite | 8.3.6 | 開発用のサーバーと、本番用のファイルを作る道具 |
| Material UI | **9.5.0** | ボタン・入力欄・モーダル・タブなどの部品集（講義と同じ道具。講義は 7 だが、ユーザーの判断で最新にする）。見た目の土台に Emotion 11 を使う |
| React Router | 8.5.1 | URL ごとに画面を切り替える（`react-router-dom` は 8 から `react-router` にまとめられた） |
| axios | 1.21.0 | サーバーとの通信（講義と同じ道具）。CSRF のトークンをヘッダーに付ける機能を最初から持つ |
| Node.js | **24**（24.22.2） | Vite・npm を動かす。画面のビルド（本番用のファイル作り）に使う。`frontend/.node-version` |
| Vitest | 5.1.0 | 画面のテスト |
| oxlint・Prettier | 1.92.0・3.9.10 | コードのチェックと書き方をそろえる道具（トレロ風アプリと同じ設定） |

### 2.2 サーバー（バックエンド）

| もの | バージョン | 説明 |
|---|---|---|
| Java | **25**（最新の LTS＝長く使える版） | |
| Spring Boot | **4.1.2**（2026-11-08 に公式サイトで最新を確認。トレロ風と同じ） | Java で Web の API を作る土台 |
| Gradle | 9.8.2 | ビルドの道具 |
| Spring Security | Spring Boot が決める | ログイン・JWT の確認・CSRF の対策 |
| **MyBatis**（mybatis-spring-boot-starter） | **4.1.0**（Spring Boot 4 対応） | DB の読み書き（ユーザーの判断。講義は jOOQ）。SQL は `src/main/resources/mapper/*.xml` に書く |
| Flyway | Spring Boot が決める | テーブルの作成・変更（講義と同じ）。SQL は `src/main/resources/db/migration/` |
| **jjwt** | **0.13.0** | JWT を作る・確かめる道具（講義と同じ。講義は 0.12 系） |
| AWS SDK for Java | **2.57.11** | S3 への画像・動画の保存、期限付き URL |
| Thumbnailator | **0.4.21** | 画像の縮小。写真の向き（Exif の Orientation）も直す |
| TwelveMonkeys imageio-webp | **3.15.3** | Java で WebP を読めるようにする |
| webp-imageio（`com.github.usefulness`） | **0.11.0** | Java で WebP を書けるようにする。中に C 言語の libwebp が入っている（Windows・Linux の x86_64／aarch64 用）。Java 25 では `--enable-native-access=ALL-UNNAMED` が必要（`build.gradle` で設定） |
| metadata-extractor | **2.22.0** | MP4 の長さ・映像の形式（H.264）を読む |
| Adobe S3Mock | **5.2.3** | 開発・テストで S3 の代わりに動かす（Docker。テストは Testcontainers のモジュール）。MinIO は公式の Docker イメージをやめ、LocalStack は起動にアカウントのトークンが要るので使わない |
| JUnit・Testcontainers | Spring Boot が決める | サーバーのテスト（テスト用の PostgreSQL 18 を Docker で動かす） |
| Checkstyle・SpotBugs | 14.4.0・4.10.5 | コードのチェック（トレロ風アプリと同じ設定。`.\gradlew.bat check` で走る） |

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
| 方式 | JWT（講義の方針）。**アクセストークン（15分）＋リフレッシュトークン（7日）**（ユーザーの判断） |
| 保存場所 | HttpOnly・SameSite の Cookie（本番は Secure も。画面の JavaScript から読めない）。講義は Authorization ヘッダーだが、JWT を盗まれにくくするため Cookie にした（ユーザーの判断） |
| CSRF | Cookie を使うので対策を有効にする（Cookie `XSRF-TOKEN`＋ヘッダー `X-XSRF-TOKEN`。トレロ風アプリと同じ） |
| 細かい点 | [authentication.md](features/authentication.md)（ユーザー登録・ログインの実装のときに書き足す） |

トレロ風アプリはセッション方式（サーバーにログインの状態を持つ）だったので、今回は違う方式を学ぶことになる。

---

## 4. 確認元

| もの | 確認したところ |
|---|---|
| Spring Boot | https://spring.io/projects/spring-boot （2026-11-08。4.1.2） |
| Material UI | `npm view @mui/material`（2026-11-08。9.5.0。React 19 に対応） |
| Terraform | HashiCorp の更新確認 API（2026-11-08。1.17.1）、AWS プロバイダーは Terraform Registry（6.72.0） |
| MyBatis・jjwt | Maven Central（2026-11-08。4.1.0・0.13.0）。MyBatis の Spring Boot 4 対応は公式サイトの対応表で確認 |
| 画像・動画・S3 の道具 | Maven Central（2026-11-08。AWS SDK 2.57.11、Thumbnailator 0.4.21、TwelveMonkeys 3.15.3、webp-imageio 0.11.0、metadata-extractor 2.22.0、S3Mock 5.2.3） |
| 画面のライブラリ | `npm view`（2026-11-08。環境構築のときに入れた版） |

実際に使っている版の正は、`backend/build.gradle` と `frontend/package.json`・`package-lock.json`。この表は人が読むための一覧なので、版を上げたらここも直す。

## 5. 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-11-08 | たたき台を作成 |
| 0.2 | 2026-11-08 | 講義の README に合わせ、Material UI 9 と Terraform 1.17 を追加（ユーザーの判断）。HTTPS は CloudFront |
| 0.3 | 2026-11-08 | 環境構築で実際に入れた版にした。DB の読み書きを Spring Data JPA から MyBatis に、JWT の道具に jjwt、通信に axios を追加。認証はアクセス＋リフレッシュトークン・Cookie・CSRF 有効（ユーザーの判断） |
| 0.4 | 2026-11-08 | 画像・動画の投稿で、AWS SDK・Thumbnailator・TwelveMonkeys・webp-imageio・metadata-extractor・S3Mock を追加（版も確定） |
