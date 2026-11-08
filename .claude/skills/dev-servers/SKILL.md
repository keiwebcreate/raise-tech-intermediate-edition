---
name: dev-servers
description: RaiseTechtimeline の開発サーバー（DB・Spring Boot・Vite）を、決まったポート（5433・8081・5174）で起動・停止する手順。動作確認でサーバーを起動するとき、ポートが使われていて起動できないとき、サーバーを止めるときに必ず使う。
---

# 開発サーバーの起動と停止

動作確認のためにサーバーを起動するときは、この手順で行う。コマンドは PowerShell で、`Intermediate_edition` フォルダを基準にする。

> 環境構築の前に書いた手順。`backend/`・`frontend/`・`compose.yaml` を作ったら、実際に動かして確かめ、違うところを直す（コンテナ名・起動したときのログの文言など）。

## 守ること

- ポートは必ず次のとおり。**別のポートでは起動しない**（`--port`・`server.port`・`compose.yaml` のポートの書き換え、一時的な変更も含む）
  - 別のポートで動かすと、Vite の `/api` の転送先（8081）や Spring Boot の DB の接続先（5433）とずれて、うまく動かないため
  - 前のアプリ（トレロ風 5173・8080・5432、レシピ 3000・8000・3308）とぶつからない番号にしてある

| ポート | 使うもの | 起動 |
|---|---|---|
| 5433 | PostgreSQL 18（Docker。`compose.yaml`） | `docker compose up -d` |
| 8081 | Spring Boot（API） | `backend` で `.\gradlew.bat bootRun` |
| 5174 | Vite（画面） | `frontend` で `npm run dev` |

- ポートが使われていたら、**何が使っているかをユーザーに伝えてから**止めて、決まったポートで起動する
- 3306（別のプロジェクトの MySQL）は、このアプリのポートではないので触らない

## 1. ポートが空いているか確かめる

```powershell
foreach ($port in 5433, 8081, 5174) {
  $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) { "$port : 使用中 → $((Get-Process -Id $conn.OwningProcess).ProcessName) (PID $($conn.OwningProcess))" } else { "$port : 空き" }
}
```

## 2. 使っているものを止める

| 使っているもの | 止め方 |
|---|---|
| Docker のコンテナ（プロセス名が `com.docker.backend`・`wslrelay` など） | Docker のプロセス自体は止めない（Docker 全体が止まる）。`docker ps --filter "publish=5433"` でコンテナを探し、`docker stop <コンテナ名>` |
| このアプリの古い Spring Boot・Vite（`java`・`node`） | Claude が背景で起動したものなら、そのタスクを止める。そうでなければ `Stop-Process -Id <PID>` |
| そのほかのプロセス | 何のプロセスかをユーザーに伝えてから `Stop-Process -Id <PID>` |

## 3. 起動する（DB → Spring Boot → Vite の順）

1. **DB**：`Intermediate_edition` で `docker compose up -d`。`docker compose exec db pg_isready -U sns_app_user -d sns_app` が `accepting connections` になるまで待つ
   - パスワードの環境変数（`SNS_DB_PASSWORD`）がないと言われたら、環境変数が読めていない。VS Code を開き直してもらう
2. **Spring Boot**：`backend` で `.\gradlew.bat bootRun` を**背景で**（`run_in_background`）起動する。ログに `Started` が出て、8081 が使用中になるまで待つ
3. **Vite**：`frontend` で `npm run dev` を**背景で**起動する。ログに `http://localhost:5174/` が出るまで待つ
   - `Port 5174 is already in use` で止まったら、2 に戻って 5174 を空ける（Vite は `strictPort: true` にして、別のポートに逃げないようにする）

## 4. 動いたか確かめる

- 画面：`Invoke-WebRequest http://localhost:5174 -UseBasicParsing` が 200
- API：`Invoke-WebRequest http://localhost:5174/api/... -UseBasicParsing -SkipHttpErrorCheck` で、Vite 経由で Spring Boot に届くか（ログインが必要な API なら 401 が返れば届いている）
- ユーザーには `http://localhost:5174` を開いてもらう

## 5. 止める

- Vite・Spring Boot：背景のタスクを止める（またはターミナルで Ctrl＋C）。残っていたら 1 で PID を調べて `Stop-Process`
- DB：`Intermediate_edition` で `docker compose stop`（データはボリュームに残る）
