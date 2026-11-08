/*
 * モックアップのサンプルデータ。
 * 本番では DB（docs/database-design.md）に入るもの。ここではブラウザのメモリに持つだけで、
 * 再読み込みすると最初の状態に戻る。
 */

/* 画像の代わりに、その場で作る絵（SVG）。外部の画像ファイルを使わないため */
function sampleImage(title, hue) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600">
    <rect width="800" height="600" fill="hsl(${hue} 55% 88%)"/>
    <rect x="60" y="70" width="680" height="460" rx="18" fill="hsl(${hue} 45% 22%)"/>
    <circle cx="100" cy="105" r="9" fill="#ff6b6b"/><circle cx="128" cy="105" r="9" fill="#ffd93d"/><circle cx="156" cy="105" r="9" fill="#6bcb77"/>
    <rect x="100" y="160" width="300" height="16" rx="8" fill="hsl(${hue} 70% 70%)"/>
    <rect x="130" y="200" width="420" height="16" rx="8" fill="hsl(${hue} 30% 60%)"/>
    <rect x="130" y="240" width="360" height="16" rx="8" fill="hsl(${hue} 30% 60%)"/>
    <rect x="100" y="280" width="240" height="16" rx="8" fill="hsl(${hue} 70% 70%)"/>
    <text x="400" y="450" font-family="sans-serif" font-size="40" font-weight="700" text-anchor="middle" fill="#fff">${title}</text>
  </svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

const DAY = 24 * 60 * 60 * 1000;
const HOUR = 60 * 60 * 1000;
const MIN = 60 * 1000;
const NOW = Date.now();

const SEED = {
  /* ログインに使うデモのアカウントは users[0]（demo@example.com / password123） */
  users: [
    { id: 1, email: 'demo@example.com', password: 'password123', username: 'raise_taro', displayName: '山田 太郎', bio: 'RaiseTech 中級編を受講中。Java と React を勉強しています。', icon: null, createdAt: NOW - 40 * DAY },
    { id: 2, email: 'hanako@example.com', password: 'password123', username: 'hanako_design', displayName: '佐藤 花子', bio: 'Web デザイナーからエンジニアへ。\nCSS が好きです。', icon: null, createdAt: NOW - 120 * DAY },
    { id: 3, email: 'ichiro@example.com', password: 'password123', username: 'ichiro_java', displayName: '鈴木 一郎', bio: 'Spring Boot で API を作っています。', icon: null, createdAt: NOW - 80 * DAY },
    { id: 4, email: 'mika@example.com', password: 'password123', username: 'mika_aws', displayName: '田中 美香', bio: 'AWS の資格を勉強中。シドニーリージョン派。', icon: null, createdAt: NOW - 200 * DAY },
    { id: 5, email: 'ken@example.com', password: 'password123', username: 'ken_react', displayName: '高橋 健', bio: '', icon: null, createdAt: NOW - 15 * DAY },
    { id: 6, email: 'yui@example.com', password: 'password123', username: 'yui_sql', displayName: '伊藤 ゆい', bio: 'SQL とデータベース設計が得意です。ER 図を描くのが好き。', icon: null, createdAt: NOW - 60 * DAY },
  ],

  /* [フォローする人, フォローされる人] */
  follows: [
    [1, 2], [2, 1],   // 太郎 ⇄ 花子（相互フォロー）
    [3, 1],           // 一郎 → 太郎（太郎から見ると「フォローバック」が出る）
    [1, 4],           // 太郎 → 美香（片思い）
    [2, 4], [4, 2], [6, 2], [5, 3], [6, 3], [2, 6],
  ],

  posts: [
    { id: 101, userId: 2, content: 'Material UI のテーマで色をまとめたら、画面の雰囲気が一気にそろった。\nprimary と secondary だけ先に決めるのがおすすめ。', media: [{ type: 'image', src: sampleImage('theme.ts', 230) }], createdAt: NOW - 12 * MIN, editedAt: null },
    { id: 102, userId: 1, content: '今日は Spring Boot の Controller → Service → Repository の流れを復習した。\n3層に分けると、どこに何を書くか迷わなくなる。', media: [], createdAt: NOW - 50 * MIN, editedAt: NOW - 40 * MIN },
    { id: 103, userId: 4, content: 'CloudFront → ALB → EC2 → RDS の構成図を描いてみた。セキュリティグループは「どこから」を絞るのが大事。', media: [{ type: 'image', src: sampleImage('ALB', 25) }, { type: 'image', src: sampleImage('EC2', 140) }, { type: 'image', src: sampleImage('RDS', 280) }], createdAt: NOW - 3 * HOUR, editedAt: null },
    { id: 104, userId: 3, content: 'JWT を HttpOnly の Cookie に入れる理由、やっと腹落ちした。JavaScript から読めないから、XSS で盗まれにくい。', media: [], createdAt: NOW - 5 * HOUR, editedAt: null },
    { id: 105, userId: 6, content: 'いいねのテーブルは (post_id, user_id) に一意制約。これで二重いいねを DB が止めてくれる。', media: [{ type: 'image', src: sampleImage('likes', 340) }, { type: 'image', src: sampleImage('follows', 190) }], createdAt: NOW - 9 * HOUR, editedAt: null },
    { id: 106, userId: 5, content: 'はじめまして！React を始めたばかりです。よろしくお願いします🙌', media: [], createdAt: NOW - 1 * DAY - 2 * HOUR, editedAt: null },
    { id: 107, userId: 2, content: '4枚の画像を並べるときは 2×2。3枚なら左に1枚大きく。', media: [{ type: 'image', src: sampleImage('1', 0) }, { type: 'image', src: sampleImage('2', 90) }, { type: 'image', src: sampleImage('3', 180) }, { type: 'image', src: sampleImage('4', 270) }], createdAt: NOW - 2 * DAY, editedAt: null },
  ],

  /* parentId が null なら投稿への直接のコメント。数字なら返信先のコメント */
  comments: [
    { id: 201, postId: 102, userId: 2, parentId: null, content: '3層、最初は面倒だけど慣れると楽だよね！', createdAt: NOW - 45 * MIN },
    { id: 202, postId: 102, userId: 1, parentId: 201, content: 'ほんとそれです。テストも書きやすくなりました', createdAt: NOW - 42 * MIN },
    { id: 203, postId: 102, userId: 3, parentId: 202, content: 'Service だけテストできるのが便利ですよね', createdAt: NOW - 30 * MIN },
    { id: 204, postId: 102, userId: 6, parentId: null, content: 'Repository の命名ルールも決めておくと迷わないです', createdAt: NOW - 20 * MIN },
    { id: 205, postId: 101, userId: 4, parentId: null, content: 'ダークモードもテーマで切り替えられるのが良い', createdAt: NOW - 8 * MIN },
    { id: 206, postId: 103, userId: 1, parentId: null, content: 'わかりやすい！ALB は CloudFront からだけ受けるんですね', createdAt: NOW - 2 * HOUR },
    { id: 207, postId: 106, userId: 2, parentId: null, content: 'ようこそ！一緒にがんばりましょう', createdAt: NOW - 1 * DAY },
  ],

  /* いいね：[投稿ID, ユーザーID] */
  likes: [
    [101, 1], [101, 4], [101, 6],
    [102, 2], [102, 3], [102, 4], [102, 5], [102, 6],
    [103, 1], [103, 2],
    [104, 6],
    [106, 1], [106, 2], [106, 3],
    [107, 1], [107, 6],
  ],

  /* コメントへのいいね：[コメントID, ユーザーID] */
  commentLikes: [
    [201, 1], [201, 3], [202, 2], [204, 1], [206, 4],
  ],
};

/* スクロールで続きを読み込む（20件ずつ）のを試せるように、古い投稿を足しておく */
(function addOlderPosts() {
  const lines = [
    'Flyway のマイグレーションは、すでにあるファイルを書き換えない。',
    'TypeScript で any を使わないようにしたら、型エラーで間違いに早く気づけた。',
    'Git のブランチ名は「種類/イシュー番号-内容」にそろえている。',
    'Docker で PostgreSQL 18 を立てた。compose.yaml 1つで済むのは楽。',
    '日本語の変換中に Enter で送信しないよう、isComposing を見る。',
    'N+1 問題、タイムラインみたいな一覧で起きやすいので注意。',
    'Terraform の plan を必ず読んでから apply する。',
    '280文字って意外と書ける。短くまとめる練習になる。',
  ];
  for (let i = 0; i < 26; i++) {
    SEED.posts.push({
      id: 300 + i,
      userId: [2, 3, 4, 5, 6, 1][i % 6],
      content: lines[i % lines.length],
      media: i % 7 === 0 ? [{ type: 'image', src: sampleImage('note ' + (i + 1), (i * 37) % 360) }] : [],
      createdAt: NOW - (3 + i) * DAY - (i % 5) * HOUR,
      editedAt: null,
    });
  }
})();
