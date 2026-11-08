/*
 * RaiseTechtimeline モックアップ（HTML・CSS・JavaScript だけ）
 *
 * - 画面は URL の # の後ろで切り替える（例：#/posts/101）。サーバーはいらない
 * - データは data.js の SEED をコピーして、ブラウザのメモリに持つ。再読み込みで最初に戻る
 * - 画面の ID（S-01 など）と文言は docs/screen-design.md・docs/features/*.md に合わせている
 */
'use strict';

/* ===== 決まりごと（機能定義書の数字） ===== */
const LIMITS = {
  content: 280,            // 投稿・コメントの本文（post.md 3 / comment.md 3）
  displayName: 50,         // 表示名（profile.md 3.2）
  bio: 160,                // 自己紹介
  images: 4,               // 画像は4枚まで
  imageBytes: 5 * 1024 * 1024,
  videoBytes: 100 * 1024 * 1024,
  videoSeconds: 140,       // 2分20秒
  pageSize: 20,            // タイムラインなどは20件ずつ
  commentPage: 50,         // コメントは50件ずつ
  searchLength: 50,
  loginFailures: 10,       // 15分に10回まで（要件 N-23。モックでは回数だけ）
};
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const ICON_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/* ===== データ（SEED をコピーして使う） ===== */
let nextId = 1000;
const db = {
  users: SEED.users.map((u) => ({ ...u })),
  follows: SEED.follows.map(([followerId, followingId], i) => ({ followerId, followingId, createdAt: NOW - (i + 1) * HOUR })),
  posts: SEED.posts.map((p) => ({ ...p, media: p.media.map((m) => ({ ...m })) })),
  comments: SEED.comments.map((c) => ({ ...c })),
  likes: SEED.likes.map(([postId, userId], i) => ({ postId, userId, createdAt: NOW - (i + 1) * MIN })),
  commentLikes: SEED.commentLikes.map(([commentId, userId], i) => ({ commentId, userId, createdAt: NOW - (i + 1) * MIN })),
};

/* ===== 画面の状態 ===== */
const ui = {
  me: null,                 // ログインしているユーザーの ID
  next: null,               // ログインの前に開こうとしていた画面
  tab: readStorage('rt-tab') || 'all',
  listLimit: LIMITS.pageSize,
  commentLimit: LIMITS.commentPage,
  expanded: new Set(),      // 返信を開いているコメント（一番上のコメントの ID）
  replyTo: null,            // 返信の入力欄を開いているコメントの ID
  composers: {},            // 投稿の入力欄ごとの画像・動画
  loginFailures: 0,
  keepScroll: false,
};

/* ===== 小さな道具 ===== */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}
/* 文字数は「見た目の1文字＝1」。絵文字も1文字（PostgreSQL の varchar と同じくコードポイントで数える） */
const countChars = (s) => [...s].length;
/* 検索用：全角・半角、大文字・小文字をそろえる */
const normalize = (s) => s.normalize('NFKC').toLowerCase();

function readStorage(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}
function writeStorage(key, value) {
  try { localStorage.setItem(key, value); } catch { /* 保存できなくても動く */ }
}

function relTime(ts) {
  const diff = Date.now() - ts;
  const d = new Date(ts);
  if (diff < MIN) return 'たった今';
  if (diff < HOUR) return `${Math.floor(diff / MIN)}分前`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}時間前`;
  if (d.getFullYear() === new Date().getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日`;
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
}
function fullTime(ts) {
  const d = new Date(ts);
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/* ===== データを探す・数える ===== */
const userById = (id) => db.users.find((u) => u.id === id);
const userByName = (name) => db.users.find((u) => u.username === String(name).toLowerCase());
const postById = (id) => db.posts.find((p) => p.id === id);
const commentById = (id) => db.comments.find((c) => c.id === id);
const me = () => userById(ui.me);

const isFollowing = (a, b) => db.follows.some((f) => f.followerId === a && f.followingId === b);
const followingOf = (id) => db.follows.filter((f) => f.followerId === id).sort((a, b) => b.createdAt - a.createdAt).map((f) => userById(f.followingId));
const followersOf = (id) => db.follows.filter((f) => f.followingId === id).sort((a, b) => b.createdAt - a.createdAt).map((f) => userById(f.followerId));

const postLikes = (postId) => db.likes.filter((l) => l.postId === postId);
const isLiked = (postId) => db.likes.some((l) => l.postId === postId && l.userId === ui.me);
const commentLikesOf = (commentId) => db.commentLikes.filter((l) => l.commentId === commentId);
const isCommentLiked = (commentId) => db.commentLikes.some((l) => l.commentId === commentId && l.userId === ui.me);

/* 投稿のコメント数は、投稿に直接付いたものだけ（X と同じ。comment.md 4.3） */
const commentCount = (postId) => db.comments.filter((c) => c.postId === postId && c.parentId === null).length;
const replyCount = (commentId) => db.comments.filter((c) => c.parentId === commentId).length;

function descendantsOf(commentId) {
  const result = [];
  const walk = (id) => db.comments.filter((c) => c.parentId === id).forEach((c) => { result.push(c); walk(c.id); });
  walk(commentId);
  return result;
}
function rootOf(comment) {
  let c = comment;
  while (c.parentId !== null) c = commentById(c.parentId);
  return c;
}

/* ===== アイコン（SVG） ===== */
const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>',
  pen: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m14 6 4 4"/>',
  heart: '<path d="M12 20s-7-4.5-9-9a4.8 4.8 0 0 1 9-3 4.8 4.8 0 0 1 9 3c-2 4.5-9 9-9 9z"/>',
  comment: '<path d="M4 5h16v11H9l-5 4z"/>',
  reply: '<path d="M10 8 4 13l6 5"/><path d="M4 13h10a6 6 0 0 1 6 6"/>',
  more: '<circle cx="5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="19" cy="12" r="1.4"/>',
  back: '<path d="M19 12H5"/><path d="m11 6-6 6 6 6"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 17-5-5-9 8"/>',
  video: '<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-3v10l-5-3z"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  logout: '<path d="M15 4h4v16h-4"/><path d="M10 8l-4 4 4 4"/><path d="M6 12h10"/>',
  left: '<path d="m15 6-6 6 6 6"/>',
  right: '<path d="m9 6 6 6-6 6"/>',
};
const icon = (name, cls = '') => `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${ICONS[name]}</svg>`;

/* ===== 共通の部品 ===== */
function avatar(user, size = 'md') {
  if (user.icon) return `<img class="avatar avatar--${size}" src="${esc(user.icon)}" alt="">`;
  const hue = (user.id * 67) % 360;
  return `<span class="avatar avatar--${size}" style="--hue:${hue}" aria-hidden="true">${esc([...user.displayName][0] || '?')}</span>`;
}
const profileHref = (user) => `#/users/${encodeURIComponent(user.username)}`;

function followedChip(user) {
  return user.id !== ui.me && isFollowing(user.id, ui.me) ? '<span class="chip-followed">フォローされています</span>' : '';
}

/* フォローボタン（profile.md 4.3）。どの画面でも同じ部品 */
function followButton(user) {
  if (!ui.me || user.id === ui.me) return '';
  const following = isFollowing(ui.me, user.id);
  const followedBy = isFollowing(user.id, ui.me);
  if (following) {
    return `<button type="button" class="btn btn--outline btn--follow is-following" data-action="follow" data-follow-user="${user.id}" aria-label="@${esc(user.username)} さんをフォロー中。押すとフォロー解除">
      <span class="when-idle">フォロー中</span><span class="when-hover">フォロー解除</span></button>`;
  }
  const label = followedBy ? 'フォローバック' : 'フォローする';
  return `<button type="button" class="btn btn--primary btn--follow" data-action="follow" data-follow-user="${user.id}">${label}</button>`;
}

/* ユーザーが並ぶところの1行（検索結果・フォロー一覧・いいねした人） */
function userRow(user) {
  const firstLine = (user.bio || '').split('\n')[0];
  return `<li class="user-row">
    <a href="${profileHref(user)}" class="user-row__avatar">${avatar(user)}</a>
    <div class="user-row__body">
      <a href="${profileHref(user)}" class="user-row__name">${esc(user.displayName)}</a>
      <div class="user-row__meta"><span class="handle">@${esc(user.username)}</span>${followedChip(user)}</div>
      ${firstLine ? `<p class="user-row__bio">${esc(firstLine)}</p>` : ''}
    </div>
    <div class="user-row__action">${followButton(user)}</div>
  </li>`;
}

/* 残りの文字数の輪（投稿・コメント共通） */
function counterRing(id) {
  return `<span class="counter" data-counter="${id}" aria-live="polite">
    <svg class="counter__ring" viewBox="0 0 24 24" aria-hidden="true"><circle class="counter__track" cx="12" cy="12" r="10"/><circle class="counter__bar" cx="12" cy="12" r="10"/></svg>
    <span class="counter__num"></span>
  </span>`;
}
function updateCounter(id, text, limit) {
  const el = $(`[data-counter="${id}"]`);
  if (!el) return;
  const len = countChars(text.trim());
  const rest = limit - len;
  const circumference = 2 * Math.PI * 10;
  $('.counter__bar', el).style.strokeDashoffset = String(circumference * (1 - Math.min(len / limit, 1)));
  el.classList.toggle('is-warn', rest <= 20 && rest >= 0);
  el.classList.toggle('is-over', rest < 0);
  $('.counter__num', el).textContent = rest <= 20 ? String(rest) : '';
  el.setAttribute('aria-label', `残り ${rest} 文字`);
}

/* ===== 投稿 ===== */
function mediaGrid(post) {
  if (!post.media.length) return '';
  if (post.media[0].type === 'video') {
    return `<div class="media media--video"><video src="${esc(post.media[0].src)}" controls preload="metadata" aria-label="投稿の動画"></video></div>`;
  }
  const n = post.media.length;
  return `<div class="media media--${n}">${post.media.map((m, i) => `
    <button type="button" class="media__item" data-action="lightbox" data-post-id="${post.id}" data-index="${i}" aria-label="画像 ${i + 1} / ${n} を大きく表示">
      <img src="${esc(m.src)}" alt="">
    </button>`).join('')}</div>`;
}

function postCard(post, variant = 'list') {
  const author = userById(post.userId);
  const likes = postLikes(post.id).length;
  const liked = isLiked(post.id);
  const comments = commentCount(post.id);
  const mine = post.userId === ui.me;
  const href = `#/posts/${post.id}`;
  const time = variant === 'detail'
    ? `<time datetime="${new Date(post.createdAt).toISOString()}">${fullTime(post.createdAt)}</time>`
    : `<a href="${href}" class="post__time"><time datetime="${new Date(post.createdAt).toISOString()}" title="${fullTime(post.createdAt)}">${relTime(post.createdAt)}</time></a>`;
  return `<article class="post post--${variant}" data-post-id="${post.id}" data-variant="${variant}" ${variant === 'list' ? `data-href="${href}"` : ''}>
    <a href="${profileHref(author)}" class="post__avatar">${avatar(author)}</a>
    <div class="post__body">
      <header class="post__head">
        <a href="${profileHref(author)}" class="post__name">${esc(author.displayName)}</a>
        <span class="handle">@${esc(author.username)}</span>
        ${variant === 'list' ? time : ''}
        ${post.editedAt ? '<span class="post__edited">編集済み</span>' : ''}
        ${mine ? `<details class="menu">
          <summary class="icon-btn" aria-label="投稿のメニュー">${icon('more')}</summary>
          <div class="menu__list">
            <button type="button" data-action="edit-post" data-post-id="${post.id}">編集</button>
            <button type="button" class="is-danger" data-action="delete-post" data-post-id="${post.id}">削除</button>
          </div>
        </details>` : ''}
      </header>
      <p class="post__content">${esc(post.content)}</p>
      ${mediaGrid(post)}
      ${variant === 'detail' ? `<p class="post__fulltime">${time}</p>` : ''}
      <footer class="post__actions">
        <a href="${href}${variant === 'list' ? '' : ''}" class="action" ${variant === 'detail' ? 'data-action="focus-comment"' : ''} aria-label="コメント ${comments} 件">
          ${icon('comment')}<span class="action__count">${comments || ''}</span>
        </a>
        <span class="action-group">
          <button type="button" class="action action--like ${liked ? 'is-liked' : ''}" data-action="like" data-post-id="${post.id}" aria-pressed="${liked}" aria-label="${liked ? 'いいねを取り消す' : 'いいねする'}">${icon('heart')}</button>
          ${likes ? `<button type="button" class="action__count action__count--link" data-action="likers" data-kind="post" data-id="${post.id}" aria-label="いいねしたユーザー ${likes} 人を見る">${likes}</button>` : ''}
        </span>
      </footer>
    </div>
  </article>`;
}

/* いいね・編集のあと、画面に出ているその投稿だけを描き直す */
function refreshPost(postId) {
  const post = postById(postId);
  $$(`.post[data-post-id="${postId}"]`).forEach((el) => {
    if (post) el.outerHTML = postCard(post, el.dataset.variant);
    else el.remove();
  });
}

/* ===== 一覧（20件ずつ。下までスクロールしたら続き） ===== */
let listObserver = null;
function mountPostList(posts, emptyHtml) {
  const list = $('#post-list');
  const sentinel = $('#list-sentinel');
  if (listObserver) listObserver.disconnect();
  if (!posts.length) {
    list.innerHTML = `<div class="empty">${emptyHtml}</div>`;
    sentinel.hidden = true;
    return;
  }
  let shown = Math.min(ui.listLimit, posts.length);
  list.innerHTML = posts.slice(0, shown).map((p) => postCard(p)).join('');
  const finish = () => { sentinel.hidden = false; sentinel.textContent = 'これ以上はありません'; sentinel.classList.add('is-end'); };
  if (shown >= posts.length) { finish(); return; }
  sentinel.hidden = false;
  sentinel.textContent = '読み込み中…';
  let loading = false;
  listObserver = new IntersectionObserver((entries) => {
    if (!entries[0].isIntersecting || loading) return;
    loading = true;
    /* サーバーから読むふり（少し待つ） */
    setTimeout(() => {
      const more = posts.slice(shown, shown + LIMITS.pageSize);
      list.insertAdjacentHTML('beforeend', more.map((p) => postCard(p)).join(''));
      shown += more.length;
      ui.listLimit = shown;
      loading = false;
      if (shown >= posts.length) { listObserver.disconnect(); finish(); }
    }, 400);
  }, { rootMargin: '200px' });
  listObserver.observe(sentinel);
}
const byNewest = (a, b) => b.createdAt - a.createdAt || b.id - a.id;

/* ===== 画面：S-03 タイムライン ===== */
function viewTimeline() {
  const tab = ui.tab;
  return {
    title: 'ホーム',
    header: false,
    html: `
      <div class="tabs" role="tablist" aria-label="タイムラインの種類">
        <button type="button" role="tab" class="tab" data-action="tab" data-tab="all" aria-selected="${tab === 'all'}">すべて</button>
        <button type="button" role="tab" class="tab" data-action="tab" data-tab="following" aria-selected="${tab === 'following'}">フォロー中</button>
      </div>
      <section class="composer-inline" aria-label="投稿する">${composerHtml('tl')}</section>
      <div id="post-list" class="post-list" role="feed" aria-label="${tab === 'all' ? 'すべての投稿' : 'フォロー中の投稿'}"></div>
      <div id="list-sentinel" class="list-end"></div>`,
    mount() {
      initComposer('tl', null);
      let posts = db.posts;
      if (tab === 'following') {
        const ids = new Set([ui.me, ...followingOf(ui.me).map((u) => u.id)]);
        posts = posts.filter((p) => ids.has(p.userId));
      }
      mountPostList([...posts].sort(byNewest), tab === 'all'
        ? '<p>まだ投稿がありません</p>'
        : '<p>まだ誰もフォローしていません。「すべて」や検索から、気になる人を探してみましょう</p><p><a class="btn btn--primary" href="#/search">ユーザーを検索する</a></p>');
    },
  };
}

/* ===== 投稿の入力欄（M-01 とタイムラインの上で共通。post.md 4.1） ===== */
function composerHtml(id, { submitLabel = '投稿する' } = {}) {
  const user = me();
  return `<form class="composer" data-composer="${id}" novalidate>
    <div class="composer__avatar">${avatar(user)}</div>
    <div class="composer__main">
      <label class="visually-hidden" for="${id}-text">投稿の本文</label>
      <textarea id="${id}-text" class="composer__text" rows="${id === 'tl' ? 2 : 4}" placeholder="いまどうしてる？" data-limit="${LIMITS.content}" data-counter-id="${id}"></textarea>
      <div class="composer__media" id="${id}-media"></div>
      <p class="field-error" id="${id}-error" role="alert"></p>
      <div class="composer__bar">
        <label class="icon-btn icon-btn--accent" data-file-label="image" title="画像（4枚まで）">
          ${icon('image')}<span class="visually-hidden">画像を選ぶ（4枚まで）</span>
          <input type="file" class="visually-hidden" accept="${IMAGE_TYPES.join(',')}" multiple data-composer-file="image" data-composer-id="${id}">
        </label>
        <label class="icon-btn icon-btn--accent" data-file-label="video" title="動画（1本）">
          ${icon('video')}<span class="visually-hidden">動画を選ぶ（1本）</span>
          <input type="file" class="visually-hidden" accept="video/mp4" data-composer-file="video" data-composer-id="${id}">
        </label>
        <span class="composer__spacer"></span>
        ${counterRing(id)}
        <button type="submit" class="btn btn--primary" data-composer-submit="${id}" disabled>${submitLabel}</button>
      </div>
    </div>
  </form>`;
}

function initComposer(id, post) {
  ui.composers[id] = { postId: post ? post.id : null, media: post ? post.media.map((m) => ({ ...m })) : [], initial: post ? post.content : '' };
  const ta = $(`#${id}-text`);
  if (post) ta.value = post.content;
  renderComposerMedia(id);
  updateComposer(id);
}

function renderComposerMedia(id) {
  const c = ui.composers[id];
  $(`#${id}-media`).innerHTML = c.media.map((m, i) => `
    <figure class="thumb ${m.type === 'video' ? 'thumb--video' : ''}">
      ${m.type === 'video' ? `<video src="${esc(m.src)}" preload="metadata" muted aria-label="選んだ動画"></video>` : `<img src="${esc(m.src)}" alt="選んだ画像 ${i + 1}">`}
      ${m.uploading ? `<figcaption class="thumb__progress"><span style="width:${m.progress}%"></span><em>送信中 ${m.progress}%</em></figcaption>` : ''}
      <button type="button" class="thumb__remove" data-action="remove-media" data-composer-id="${id}" data-index="${i}" aria-label="${m.type === 'video' ? '動画' : `画像 ${i + 1}`} を外す">${icon('close')}</button>
    </figure>`).join('');
}

function updateComposer(id) {
  const c = ui.composers[id];
  const ta = $(`#${id}-text`);
  if (!c || !ta) return;
  const len = countChars(ta.value.trim());
  updateCounter(id, ta.value, LIMITS.content);
  const uploading = c.media.some((m) => m.uploading);
  $(`[data-composer-submit="${id}"]`).disabled = len === 0 || len > LIMITS.content || uploading;
  const hasVideo = c.media.some((m) => m.type === 'video');
  const imageLabel = $(`[data-composer-id="${id}"][data-composer-file="image"]`);
  const videoLabel = $(`[data-composer-id="${id}"][data-composer-file="video"]`);
  /* 画像と動画は混ぜない。画像は4枚まで */
  imageLabel.disabled = hasVideo || c.media.length >= LIMITS.images;
  videoLabel.disabled = c.media.length > 0;
  imageLabel.parentElement.classList.toggle('is-disabled', imageLabel.disabled);
  videoLabel.parentElement.classList.toggle('is-disabled', videoLabel.disabled);
}

function composerError(id, message) {
  const el = $(`#${id}-error`);
  if (el) el.textContent = message || '';
}

function addImages(id, files) {
  const c = ui.composers[id];
  composerError(id, '');
  /* だめな画像があっても、それまでに足した画像は残して表示する */
  let error = '';
  for (const file of files) {
    if (c.media.some((m) => m.type === 'video')) { error = '画像と動画は一緒に投稿できません'; break; }
    if (c.media.length >= LIMITS.images) { error = '画像は4枚までです'; break; }
    if (!IMAGE_TYPES.includes(file.type)) { error = 'JPEG・PNG・WebP・GIF の画像を選んでください'; continue; }
    if (file.size > LIMITS.imageBytes) { error = '画像は1枚5MBまでです'; continue; }
    c.media.push({ type: 'image', src: URL.createObjectURL(file) });
  }
  composerError(id, error);
  renderComposerMedia(id);
  updateComposer(id);
}

function addVideo(id, file) {
  const c = ui.composers[id];
  composerError(id, '');
  if (c.media.length) return composerError(id, '画像と動画は一緒に投稿できません');
  if (file.type !== 'video/mp4') return composerError(id, 'MP4 の動画を選んでください');
  if (file.size > LIMITS.videoBytes) return composerError(id, '動画は100MB・2分20秒までです');
  const src = URL.createObjectURL(file);
  const probe = document.createElement('video');
  probe.preload = 'metadata';
  probe.onloadedmetadata = () => {
    if (probe.duration > LIMITS.videoSeconds) { URL.revokeObjectURL(src); return composerError(id, '動画は100MB・2分20秒までです'); }
    /* 本番では選んだときから S3 へ直接送る（post.md 3.2）。ここでは進み具合だけ見せる */
    const item = { type: 'video', src, uploading: true, progress: 0 };
    c.media.push(item);
    renderComposerMedia(id);
    updateComposer(id);
    const timer = setInterval(() => {
      item.progress = Math.min(item.progress + 15, 100);
      if (item.progress === 100) { item.uploading = false; clearInterval(timer); }
      if (!c.media.includes(item)) { clearInterval(timer); return; }
      renderComposerMedia(id);
      updateComposer(id);
    }, 250);
  };
  probe.onerror = () => composerError(id, 'MP4 の動画を選んでください');
  probe.src = src;
}

function composerDirty(id) {
  const c = ui.composers[id];
  const ta = $(`#${id}-text`);
  if (!c || !ta) return false;
  if (c.postId) {
    const post = postById(c.postId);
    return ta.value !== c.initial || JSON.stringify(c.media.map((m) => m.src)) !== JSON.stringify(post.media.map((m) => m.src));
  }
  return ta.value.trim() !== '' || c.media.length > 0;
}

function submitComposer(id) {
  const c = ui.composers[id];
  const text = $(`#${id}-text`).value.trim();
  if (!text) return composerError(id, '本文を入力してください');
  if (countChars(text) > LIMITS.content) return composerError(id, '280文字以内で入力してください');
  const media = c.media.map(({ type, src }) => ({ type, src }));
  if (c.postId) {
    const post = postById(c.postId);
    if (!post || post.userId !== ui.me) { closeModal(true); return toast('この投稿は見つかりません'); }
    post.content = text;
    post.media = media;
    post.editedAt = Date.now();
    closeModal(true);
    refreshPost(post.id);
    toast('投稿を更新しました');
    return;
  }
  db.posts.push({ id: ++nextId, userId: ui.me, content: text, media, createdAt: Date.now(), editedAt: null });
  if (id === 'm') closeModal(true);
  toast('投稿しました');
  if (location.hash === '' || location.hash === '#/') render();
  else location.hash = '#/';
}

/* ===== 画面：S-04 投稿詳細 ===== */
function viewPost(postId) {
  const post = postById(postId);
  if (!post) {
    return { title: '投稿', header: true, html: '<div class="empty"><p>この投稿は見つかりません</p><p><a class="btn btn--outline" href="#/">ホームへ戻る</a></p></div>' };
  }
  const tops = db.comments.filter((c) => c.postId === postId && c.parentId === null).sort((a, b) => a.createdAt - b.createdAt);
  const shown = tops.slice(0, ui.commentLimit);
  return {
    title: '投稿',
    header: true,
    html: `
      ${postCard(post, 'detail')}
      <form class="comment-form" data-comment-form="${postId}" novalidate>
        ${avatar(me())}
        <div class="comment-form__main">
          <label class="visually-hidden" for="comment-text">コメント</label>
          <textarea id="comment-text" rows="2" placeholder="コメントを入力" data-limit="${LIMITS.content}" data-counter-id="comment"></textarea>
          <p class="field-error" id="comment-error" role="alert"></p>
          <div class="comment-form__bar">${counterRing('comment')}<button type="submit" class="btn btn--primary" data-submit-for="comment" disabled>コメントする</button></div>
        </div>
      </form>
      <ol class="comments" aria-label="コメント">
        ${shown.map((c) => commentThread(c)).join('')}
      </ol>
      ${tops.length > shown.length ? `<p class="more"><button type="button" class="btn btn--outline" data-action="more-comments">さらに表示</button></p>` : ''}
      ${tops.length === 0 ? '<p class="empty empty--small">まだコメントはありません</p>' : ''}`,
    mount() {
      updateCounter('comment', '', LIMITS.content);
      if (ui.replyTo) {
        const ta = $('#reply-text');
        if (ta) ta.focus();
      }
    },
  };
}

function commentThread(top) {
  const replies = descendantsOf(top.id).sort((a, b) => a.createdAt - b.createdAt);
  const open = ui.expanded.has(top.id);
  return `<li class="comment-thread">
    ${commentItem(top, top)}
    ${replies.length ? `<button type="button" class="thread-toggle" data-action="toggle-thread" data-id="${top.id}" aria-expanded="${open}">
      ${open ? '返信を隠す' : `返信を表示（${replies.length}件）`}</button>` : ''}
    ${open && replies.length ? `<ol class="replies">${replies.map((r) => `<li>${commentItem(r, top)}</li>`).join('')}</ol>` : ''}
  </li>`;
}

function commentItem(c, top) {
  const author = userById(c.userId);
  const likes = commentLikesOf(c.id).length;
  const liked = isCommentLiked(c.id);
  const replies = replyCount(c.id);
  const parent = c.parentId !== null && c.parentId !== top.id ? userById(commentById(c.parentId).userId) : null;
  return `<article class="comment" id="comment-${c.id}">
    <a href="${profileHref(author)}">${avatar(author, 'sm')}</a>
    <div class="comment__body">
      <header class="comment__head">
        <a href="${profileHref(author)}" class="post__name">${esc(author.displayName)}</a>
        <span class="handle">@${esc(author.username)}</span>
        <time datetime="${new Date(c.createdAt).toISOString()}" title="${fullTime(c.createdAt)}" class="post__time">${relTime(c.createdAt)}</time>
        <span class="comment__follow">${followButton(author)}</span>
      </header>
      ${parent ? `<p class="reply-to">返信先：<a href="${profileHref(parent)}">@${esc(parent.username)}</a></p>` : ''}
      <p class="comment__content">${esc(c.content)}</p>
      <footer class="post__actions post__actions--small">
        <button type="button" class="action" data-action="reply" data-id="${c.id}" aria-label="返信する（返信 ${replies} 件）">${icon('reply')}<span class="action__count">${replies || ''}</span></button>
        <span class="action-group">
          <button type="button" class="action action--like ${liked ? 'is-liked' : ''}" data-action="like-comment" data-id="${c.id}" aria-pressed="${liked}" aria-label="${liked ? 'いいねを取り消す' : 'いいねする'}">${icon('heart')}</button>
          ${likes ? `<button type="button" class="action__count action__count--link" data-action="likers" data-kind="comment" data-id="${c.id}" aria-label="いいねしたユーザー ${likes} 人を見る">${likes}</button>` : ''}
        </span>
        ${c.userId === ui.me ? `<button type="button" class="action action--text is-danger" data-action="delete-comment" data-id="${c.id}">削除</button>` : ''}
      </footer>
      ${ui.replyTo === c.id ? `<form class="reply-form" data-reply-form="${c.id}" novalidate>
        <p class="reply-to">返信先：@${esc(author.username)}</p>
        <label class="visually-hidden" for="reply-text">返信</label>
        <textarea id="reply-text" rows="2" placeholder="返信を入力" data-limit="${LIMITS.content}" data-counter-id="reply"></textarea>
        <p class="field-error" id="reply-error" role="alert"></p>
        <div class="comment-form__bar">${counterRing('reply')}
          <button type="button" class="btn btn--text" data-action="cancel-reply">キャンセル</button>
          <button type="submit" class="btn btn--primary" data-submit-for="reply" disabled>返信する</button>
        </div>
      </form>` : ''}
    </div>
  </article>`;
}

function addComment(postId, parentId, text, errorId) {
  const t = text.trim();
  if (!t) { $(`#${errorId}`).textContent = 'コメントを入力してください'; return false; }
  if (countChars(t) > LIMITS.content) { $(`#${errorId}`).textContent = '280文字以内で入力してください'; return false; }
  if (!postById(postId)) { toast('この投稿は見つかりません'); return false; }
  if (parentId !== null && !commentById(parentId)) { toast('返信先のコメントは削除されました'); return false; }
  const c = { id: ++nextId, postId, userId: ui.me, parentId, content: t, createdAt: Date.now() };
  db.comments.push(c);
  if (parentId !== null) ui.expanded.add(rootOf(c).id);
  return true;
}

function deleteCommentTree(commentId) {
  const ids = new Set([commentId, ...descendantsOf(commentId).map((c) => c.id)]);
  db.comments = db.comments.filter((c) => !ids.has(c.id));
  db.commentLikes = db.commentLikes.filter((l) => !ids.has(l.commentId));
}

/* ===== 画面：S-05 プロフィール ===== */
function viewProfile(username) {
  const user = userByName(username);
  if (!user) return { title: 'プロフィール', header: true, html: '<div class="empty"><p>このアカウントは存在しません</p></div>' };
  const joined = new Date(user.createdAt);
  const posts = db.posts.filter((p) => p.userId === user.id).sort(byNewest);
  return {
    title: user.displayName,
    subtitle: `${posts.length} 件の投稿`,
    header: true,
    html: `
      <section class="profile">
        <div class="profile__top">
          ${avatar(user, 'xl')}
          <div class="profile__action">${user.id === ui.me ? '<a class="btn btn--outline" href="#/settings/profile">プロフィールを編集</a>' : followButton(user)}</div>
        </div>
        <h2 class="profile__name">${esc(user.displayName)}</h2>
        <p class="profile__handle"><span class="handle">@${esc(user.username)}</span>${followedChip(user)}</p>
        ${user.bio ? `<p class="profile__bio">${esc(user.bio)}</p>` : ''}
        <p class="profile__joined">${joined.getFullYear()}年${joined.getMonth() + 1}月から利用しています</p>
        <p class="profile__counts">
          <a href="${profileHref(user)}/following"><b>${followingOf(user.id).length}</b> フォロー</a>
          <a href="${profileHref(user)}/followers"><b>${followersOf(user.id).length}</b> フォロワー</a>
        </p>
      </section>
      <h3 class="section-title">投稿</h3>
      <div id="post-list" class="post-list" role="feed" aria-label="${esc(user.displayName)} さんの投稿"></div>
      <div id="list-sentinel" class="list-end"></div>`,
    mount() { mountPostList(posts, '<p>まだ投稿がありません</p>'); },
  };
}

/* ===== 画面：S-06 プロフィール編集 ===== */
let profileDraft = null;
function viewEditProfile() {
  const user = me();
  profileDraft = { icon: user.icon };
  return {
    title: 'プロフィールを編集',
    header: true,
    headerAction: '<button type="submit" form="profile-form" class="btn btn--primary">保存する</button>',
    html: `
      <form id="profile-form" class="profile-form" novalidate>
        <div class="profile-form__icon">
          <span id="icon-preview">${avatar(user, 'xl')}</span>
          <div class="profile-form__icon-actions">
            <label class="btn btn--outline">画像を選ぶ<input type="file" class="visually-hidden" id="icon-input" accept="${ICON_TYPES.join(',')}"></label>
            <button type="button" class="btn btn--text" data-action="remove-icon">画像を外す</button>
          </div>
          <p class="field-hint">JPEG・PNG・WebP、5MB まで。正方形に切り取って表示します</p>
          <p class="field-error" id="icon-error" role="alert"></p>
        </div>
        <div class="field">
          <label for="pf-name">表示名</label>
          <input id="pf-name" type="text" value="${esc(user.displayName)}" data-count-max="${LIMITS.displayName}" autocomplete="nickname">
          <p class="field-meta"><span class="field-error" id="pf-name-error" role="alert"></span><span data-count-for="pf-name"></span></p>
        </div>
        <div class="field">
          <label for="pf-bio">自己紹介</label>
          <textarea id="pf-bio" rows="4" data-count-max="${LIMITS.bio}">${esc(user.bio)}</textarea>
          <p class="field-meta"><span class="field-error" id="pf-bio-error" role="alert"></span><span data-count-for="pf-bio"></span></p>
        </div>
        <p class="field-hint">ユーザー名（@${esc(user.username)}）とメールアドレスは変えられません</p>
      </form>`,
    mount() { $$('[data-count-max]').forEach(updateFieldCount); },
  };
}
function updateFieldCount(input) {
  const out = $(`[data-count-for="${input.id}"]`);
  if (!out) return;
  const max = Number(input.dataset.countMax);
  const len = countChars(input.value.trim());
  out.textContent = `${len}/${max}`;
  out.classList.toggle('is-over', len > max);
}
function profileDirty() {
  const user = me();
  const name = $('#pf-name');
  if (!name || !profileDraft) return false;
  return name.value !== user.displayName || $('#pf-bio').value !== user.bio || profileDraft.icon !== user.icon;
}
function saveProfile() {
  const user = me();
  const name = $('#pf-name').value.trim();
  const bio = $('#pf-bio').value.trim();
  $('#pf-name-error').textContent = '';
  $('#pf-bio-error').textContent = '';
  let ok = true;
  if (!name) { $('#pf-name-error').textContent = '表示名を入力してください'; ok = false; }
  else if (countChars(name) > LIMITS.displayName) { $('#pf-name-error').textContent = '表示名は50文字以内で入力してください'; ok = false; }
  if (countChars(bio) > LIMITS.bio) { $('#pf-bio-error').textContent = '自己紹介は160文字以内で入力してください'; ok = false; }
  if (!ok) return;
  user.displayName = name;
  user.bio = bio;
  user.icon = profileDraft.icon;
  profileDraft = null;
  toast('プロフィールを更新しました');
  location.hash = profileHref(user);
}

/* ===== 画面：S-07 フォロー・フォロワー一覧 ===== */
function viewFollowList(username, kind) {
  const user = userByName(username);
  if (!user) return { title: 'フォロー', header: true, html: '<div class="empty"><p>このアカウントは存在しません</p></div>' };
  const list = kind === 'following' ? followingOf(user.id) : followersOf(user.id);
  const base = profileHref(user);
  return {
    title: user.displayName,
    subtitle: `@${user.username}`,
    header: true,
    html: `
      <nav class="tabs" aria-label="フォローの種類">
        <a class="tab" href="${base}/following" aria-current="${kind === 'following' ? 'page' : 'false'}">フォロー中</a>
        <a class="tab" href="${base}/followers" aria-current="${kind === 'followers' ? 'page' : 'false'}">フォロワー</a>
      </nav>
      ${list.length ? `<ul class="user-list">${list.map(userRow).join('')}</ul>`
        : `<div class="empty"><p>${kind === 'following' ? 'まだ誰もフォローしていません' : 'まだフォロワーはいません'}</p></div>`}`,
  };
}

/* ===== 画面：S-08 ユーザー検索（user-search.md） ===== */
function viewSearch(q) {
  return {
    title: '検索',
    header: false,
    html: `
      <div class="search-bar">
        ${icon('search')}
        <label class="visually-hidden" for="search-input">ユーザーを検索</label>
        <input id="search-input" type="search" placeholder="ユーザーを検索" value="${esc(q)}" maxlength="${LIMITS.searchLength}" autocomplete="off">
      </div>
      <div id="search-results" aria-live="polite"></div>`,
    mount() { renderSearch(q); },
  };
}
function searchUsers(raw) {
  const q = normalize(raw.trim()).replace(/^@/, '');
  if (!q) return null;
  const followers = (u) => followersOf(u.id).length;
  const rank = (u) => {
    const name = normalize(u.username);
    const display = normalize(u.displayName);
    if (name === q) return 0;
    if (name.startsWith(q) || display.startsWith(q)) return 1;
    return 2;
  };
  return db.users
    .filter((u) => normalize(u.username).includes(q) || normalize(u.displayName).includes(q))
    .sort((a, b) => rank(a) - rank(b) || followers(b) - followers(a))
    .slice(0, LIMITS.pageSize);
}
function renderSearch(q) {
  const out = $('#search-results');
  if (!out) return;
  const result = searchUsers(q || '');
  if (result === null) { out.innerHTML = '<p class="empty empty--small">ユーザー名や名前で検索できます</p>'; return; }
  if (!result.length) { out.innerHTML = `<p class="empty empty--small">「${esc(q.trim())}」に一致するユーザーは見つかりませんでした</p>`; return; }
  out.innerHTML = `<ul class="user-list">${result.map(userRow).join('')}</ul>`;
}
let searchTimer = null;
function scheduleSearch(value, delay = 300) {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    /* URL に入れる（戻る・再読み込みで同じ結果。user-search.md 4.1）。hashchange を起こさない書き方 */
    history.replaceState(null, '', `#/search${value.trim() ? `?q=${encodeURIComponent(value.trim())}` : ''}`);
    renderSearch(value);
  }, delay);
}

/* ===== 画面：S-01 ログイン・S-02 新規登録 ===== */
function viewLogin() {
  return {
    title: 'ログイン',
    html: `
      <form class="auth-card" id="login-form" novalidate>
        <h1 class="brand brand--large">RaiseTechtimeline</h1>
        <p class="auth-card__lead">学んだことを、短い言葉で残していこう。</p>
        <div class="field"><label for="login-email">メールアドレス</label><input id="login-email" type="email" autocomplete="email" required></div>
        <div class="field"><label for="login-password">パスワード</label><input id="login-password" type="password" autocomplete="current-password" required></div>
        <p class="field-error" id="login-error" role="alert"></p>
        <button type="submit" class="btn btn--primary btn--block">ログイン</button>
        <p class="auth-card__switch">アカウントをお持ちでない方は <a href="#/signup">新規登録</a></p>
        <div class="demo-note">
          <p>モックアップ用のアカウント：<br>demo@example.com ／ password123</p>
          <button type="button" class="btn btn--text" data-action="fill-demo">入力する</button>
        </div>
      </form>`,
  };
}
function viewSignup() {
  const field = (id, label, type, autocomplete, hint = '', prefix = '') => `
    <div class="field">
      <label for="${id}">${label}</label>
      <div class="input-wrap">${prefix ? `<span class="input-prefix">${prefix}</span>` : ''}<input id="${id}" type="${type}" autocomplete="${autocomplete}" aria-describedby="${id}-error${hint ? ` ${id}-hint` : ''}"></div>
      ${hint ? `<p class="field-hint" id="${id}-hint">${hint}</p>` : ''}
      <p class="field-error" id="${id}-error" role="alert"></p>
    </div>`;
  return {
    title: '新規登録',
    html: `
      <form class="auth-card" id="signup-form" novalidate>
        <h1 class="auth-card__title">アカウントを作成</h1>
        ${field('su-email', 'メールアドレス', 'email', 'email')}
        ${field('su-username', 'ユーザー名', 'text', 'username', '3〜15文字の半角英数字と _。あとから変えられません', '@')}
        ${field('su-name', '表示名', 'text', 'nickname', '50文字まで')}
        ${field('su-password', 'パスワード', 'password', 'new-password', '8〜64文字の半角英数字・記号')}
        ${field('su-password2', 'パスワード（確認）', 'password', 'new-password')}
        <button type="submit" class="btn btn--primary btn--block">登録する</button>
        <p class="auth-card__switch">アカウントをお持ちの方は <a href="#/login">ログイン</a></p>
      </form>`,
  };
}
function submitSignup() {
  const v = (id) => $(`#${id}`).value;
  const errors = {};
  const email = v('su-email').trim().toLowerCase();
  const username = v('su-username').trim();
  const name = v('su-name').trim();
  const pw = v('su-password');
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) errors['su-email'] = 'メールアドレスの形式で入力してください';
  else if (db.users.some((u) => u.email === email)) errors['su-email'] = 'このメールアドレスは登録済みです';
  if (!/^[A-Za-z0-9_]{3,15}$/.test(username)) errors['su-username'] = 'ユーザー名は3〜15文字の半角英数字と _ で入力してください';
  else if (userByName(username)) errors['su-username'] = 'このユーザー名は使われています';
  if (!name) errors['su-name'] = '表示名を入力してください';
  else if (countChars(name) > LIMITS.displayName) errors['su-name'] = '表示名は50文字以内で入力してください';
  if (!/^[\x21-\x7E]{8,64}$/.test(pw)) errors['su-password'] = 'パスワードは8〜64文字の半角英数字・記号で入力してください';
  if (pw !== v('su-password2')) errors['su-password2'] = 'パスワードが一致しません';
  $$('#signup-form .field-error').forEach((el) => { el.textContent = errors[el.id.replace('-error', '')] || ''; });
  $$('#signup-form input').forEach((el) => el.setAttribute('aria-invalid', String(Boolean(errors[el.id]))));
  const first = Object.keys(errors)[0];
  if (first) { $(`#${first}`).focus(); return; }
  const user = { id: ++nextId, email, password: pw, username: username.toLowerCase(), displayName: name, bio: '', icon: null, createdAt: Date.now() };
  db.users.push(user);
  ui.me = user.id;
  ui.tab = 'all';  // フォローしている人がまだいないので「すべて」（authentication.md 4.1）
  writeStorage('rt-tab', 'all');
  toast('登録しました。ようこそ！');
  location.hash = '#/';
}
function submitLogin() {
  const email = $('#login-email').value.trim().toLowerCase();
  const pw = $('#login-password').value;
  const error = $('#login-error');
  if (ui.loginFailures >= LIMITS.loginFailures) { error.textContent = 'しばらくしてから再度お試しください'; return; }
  const user = db.users.find((u) => u.email === email && u.password === pw);
  if (!user) {
    ui.loginFailures += 1;
    error.textContent = 'メールアドレスまたはパスワードが正しくありません';
    return;
  }
  ui.loginFailures = 0;
  ui.me = user.id;
  const next = ui.next;
  ui.next = null;
  location.hash = next || '#/';
}

/* ===== 画面：S-09 エラー ===== */
function viewNotFound() {
  return { title: 'ページが見つかりません', header: true, html: '<div class="empty"><p>ページが見つかりません</p><p><a class="btn btn--outline" href="#/">ホームへ戻る</a></p></div>' };
}

/* ===== 画面の枠（メニュー） ===== */
function navLink(href, name, label, current) {
  return `<a href="${href}" class="nav__link" ${current ? 'aria-current="page"' : ''}>${icon(name)}<span class="nav__label">${label}</span></a>`;
}
function accountMenu(cls) {
  const user = me();
  return `<details class="menu account ${cls}">
    <summary class="account__summary" aria-label="アカウントのメニュー">${avatar(user, 'sm')}<span class="account__text"><b>${esc(user.displayName)}</b><span class="handle">@${esc(user.username)}</span></span></summary>
    <div class="menu__list menu__list--up">
      <button type="button" data-action="logout">${icon('logout')}ログアウト</button>
    </div>
  </details>`;
}
function shell(view, route) {
  const user = me();
  const section = route.parts[0] || 'home';
  const isMine = route.parts[0] === 'users' && route.parts[1] && route.parts[1].toLowerCase() === user.username;
  return `
    <div class="layout">
      <nav class="nav" aria-label="メインメニュー">
        <a href="#/" class="brand nav__brand" aria-label="RaiseTechtimeline ホーム"><span class="brand__mark" aria-hidden="true">R</span><span class="nav__label">RaiseTechtimeline</span></a>
        ${navLink('#/', 'home', 'ホーム', section === 'home')}
        ${navLink('#/search', 'search', '検索', section === 'search')}
        ${navLink(profileHref(user), 'user', 'プロフィール', isMine)}
        <button type="button" class="btn btn--primary nav__post" data-action="compose">${icon('pen')}<span class="nav__label">投稿する</span></button>
        ${accountMenu('account--side')}
      </nav>
      <main id="main" class="main" tabindex="-1">
        <header class="topbar ${view.header ? '' : 'topbar--plain'}">
          ${view.header ? `<button type="button" class="icon-btn" data-action="back" aria-label="戻る">${icon('back')}</button>` : ''}
          <div class="topbar__title"><h1>${esc(view.title)}</h1>${view.subtitle ? `<p>${esc(view.subtitle)}</p>` : ''}</div>
          ${view.headerAction || ''}
          ${accountMenu('account--top')}
        </header>
        ${view.html}
      </main>
      <button type="button" class="fab" data-action="compose" aria-label="投稿する">${icon('pen')}</button>
    </div>`;
}

/* ===== 画面の切り替え（ルーター） ===== */
function parseHash() {
  const raw = location.hash.slice(1) || '/';
  const [path, qs] = raw.split('?');
  return { path, params: new URLSearchParams(qs || ''), parts: path.split('/').filter(Boolean).map(decodeURIComponent) };
}

function render() {
  const route = parseHash();
  const isAuthPage = route.path === '/login' || route.path === '/signup';
  closeAllDialogs();
  /* ログインしていない人は、新規登録・ログイン以外を見られない（A-04） */
  if (!ui.me && !isAuthPage) {
    ui.next = location.hash || '#/';
    location.replace('#/login');
    return;
  }
  if (ui.me && isAuthPage) { location.replace('#/'); return; }

  const [a, b, c] = route.parts;
  let view;
  if (route.path === '/login') view = viewLogin();
  else if (route.path === '/signup') view = viewSignup();
  else if (route.parts.length === 0) view = viewTimeline();
  else if (a === 'posts' && b && !c && /^\d+$/.test(b)) view = viewPost(Number(b));
  else if (a === 'users' && b && !c) view = viewProfile(b);
  else if (a === 'users' && b && (c === 'following' || c === 'followers') && route.parts.length === 3) view = viewFollowList(b, c);
  else if (route.path === '/settings/profile') view = viewEditProfile();
  else if (route.path === '/search') view = viewSearch(route.params.get('q') || '');
  else view = viewNotFound();

  const app = $('#app');
  const scroll = window.scrollY;
  app.innerHTML = isAuthPage ? `<main id="main" class="auth">${view.html}</main>` : shell(view, route);
  document.title = `${view.title} | RaiseTechtimeline`;
  if (view.mount) view.mount();
  if (ui.keepScroll) window.scrollTo(0, scroll);
  else window.scrollTo(0, 0);
  ui.keepScroll = false;
}
/* 同じ画面のまま描き直す（スクロール位置を保つ） */
function rerender() {
  ui.keepScroll = true;
  render();
}

window.addEventListener('hashchange', () => {
  ui.listLimit = LIMITS.pageSize;
  ui.commentLimit = LIMITS.commentPage;
  ui.replyTo = null;
  render();
});

/* ===== モーダル（<dialog>） ===== */
const modal = $('#modal');
const confirmBox = $('#confirm');
const lightbox = $('#lightbox');
let confirmResolve = null;

function closeAllDialogs() {
  if (modal.open) modal.close();
  if (lightbox.open) lightbox.close();
  if (confirmBox.open) { confirmBox.close(); if (confirmResolve) confirmResolve(false); confirmResolve = null; }
}

/* M-03 確認ダイアログ */
function confirmDialog({ message, ok, danger = false }) {
  confirmBox.innerHTML = `
    <form method="dialog" class="confirm">
      <p id="confirm-title" class="confirm__message">${esc(message)}</p>
      <div class="confirm__actions">
        <button value="cancel" class="btn btn--outline">キャンセル</button>
        <button value="ok" class="btn ${danger ? 'btn--danger' : 'btn--primary'}">${esc(ok)}</button>
      </div>
    </form>`;
  confirmBox.returnValue = '';  // Esc で閉じたときに、前回の答えが残らないように
  confirmBox.showModal();
  $('button[value="cancel"]', confirmBox).focus();
  return new Promise((resolve) => {
    confirmResolve = resolve;
    confirmBox.addEventListener('close', () => {
      if (confirmResolve) confirmResolve(confirmBox.returnValue === 'ok');
      confirmResolve = null;
    }, { once: true });
  });
}

/* M-01 投稿・編集 */
function openComposeModal(post = null) {
  modal.innerHTML = `<div class="modal__inner">
    <div class="modal__head">
      <button type="button" class="icon-btn" data-action="close-modal" aria-label="閉じる">${icon('close')}</button>
      <h2 id="modal-title">${post ? '投稿を編集' : '投稿する'}</h2>
    </div>
    ${composerHtml('m', { submitLabel: post ? '保存する' : '投稿する' })}</div>`;
  modal.dataset.kind = 'compose';
  modal.showModal();
  initComposer('m', post);
  $('#m-text').focus();
}
async function closeModal(force = false) {
  if (!force && modal.dataset.kind === 'compose' && composerDirty('m')) {
    const discard = await confirmDialog({ message: '編集内容を破棄しますか？', ok: '破棄する' });
    if (!discard) return;
  }
  modal.close();
}
modal.addEventListener('cancel', (e) => { e.preventDefault(); closeModal(); });
modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

/* M-02 いいねしたユーザー */
function openLikers(kind, id) {
  const rows = (kind === 'post' ? postLikes(id) : commentLikesOf(id)).sort((a, b) => b.createdAt - a.createdAt).map((l) => userById(l.userId));
  modal.innerHTML = `<div class="modal__inner">
    <div class="modal__head">
      <button type="button" class="icon-btn" data-action="close-modal" aria-label="閉じる">${icon('close')}</button>
      <h2 id="modal-title">いいねしたユーザー</h2>
    </div>
    ${rows.length ? `<ul class="user-list">${rows.map(userRow).join('')}</ul>` : '<p class="empty empty--small">まだいいねはありません</p>'}</div>`;
  modal.dataset.kind = 'likers';
  modal.dataset.likers = `${kind}:${id}`;
  modal.showModal();
}

/* 画像の拡大表示（← → で移る） */
let lightboxState = null;
function openLightbox(postId, index) {
  lightboxState = { postId, index };
  drawLightbox();
  lightbox.showModal();
}
function drawLightbox() {
  const post = postById(lightboxState.postId);
  const images = post.media;
  const i = lightboxState.index;
  lightbox.innerHTML = `
    <button type="button" class="lightbox__close icon-btn" data-action="close-lightbox" aria-label="閉じる">${icon('close')}</button>
    ${images.length > 1 ? `<button type="button" class="lightbox__nav lightbox__nav--prev icon-btn" data-action="lightbox-move" data-step="-1" aria-label="前の画像">${icon('left')}</button>` : ''}
    <img src="${esc(images[i].src)}" alt="画像 ${i + 1} / ${images.length}">
    ${images.length > 1 ? `<button type="button" class="lightbox__nav lightbox__nav--next icon-btn" data-action="lightbox-move" data-step="1" aria-label="次の画像">${icon('right')}</button>` : ''}
    <p class="lightbox__count">${i + 1} / ${images.length}</p>`;
}
lightbox.addEventListener('keydown', (e) => {
  if (!lightboxState) return;
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    const n = postById(lightboxState.postId).media.length;
    lightboxState.index = (lightboxState.index + (e.key === 'ArrowRight' ? 1 : -1) + n) % n;
    drawLightbox();
  }
});
lightbox.addEventListener('click', (e) => { if (e.target === lightbox) lightbox.close(); });

/* 通知（画面の下に少しの間だけ出る） */
let toastTimer = null;
function toast(message) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.add('is-shown');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('is-shown'), 3000);
}

/* ===== 操作（クリックなど）をまとめて受ける ===== */
async function toggleFollow(userId) {
  const user = userById(userId);
  if (isFollowing(ui.me, userId)) {
    const ok = await confirmDialog({ message: `@${user.username} さんのフォローを解除しますか？`, ok: 'フォロー解除' });
    if (!ok) return;
    db.follows = db.follows.filter((f) => !(f.followerId === ui.me && f.followingId === userId));
  } else {
    db.follows.push({ followerId: ui.me, followingId: userId, createdAt: Date.now() });
  }
  /* 画面に出ているその人のフォローボタンを描き直す */
  $$(`[data-follow-user="${userId}"]`).forEach((el) => { el.outerHTML = followButton(user); });
  /* プロフィールとフォロー一覧は、数も変わるので描き直す */
  const { parts } = parseHash();
  if (parts[0] === 'users' && !modal.open) rerender();
}

async function deletePost(postId) {
  const ok = await confirmDialog({ message: 'この投稿を削除しますか？ コメントといいねも消えます', ok: '削除する', danger: true });
  if (!ok) return;
  const commentIds = new Set(db.comments.filter((c) => c.postId === postId).map((c) => c.id));
  db.posts = db.posts.filter((p) => p.id !== postId);
  db.comments = db.comments.filter((c) => c.postId !== postId);
  db.likes = db.likes.filter((l) => l.postId !== postId);
  db.commentLikes = db.commentLikes.filter((l) => !commentIds.has(l.commentId));
  toast('投稿を削除しました');
  if (parseHash().parts[0] === 'posts') location.hash = '#/';
  else refreshPost(postId);
}

async function deleteComment(commentId) {
  const hasReplies = replyCount(commentId) > 0;
  const ok = await confirmDialog({
    message: hasReplies ? 'このコメントを削除しますか？ このコメントへの返信も消えます' : 'このコメントを削除しますか？',
    ok: '削除する', danger: true,
  });
  if (!ok) return;
  deleteCommentTree(commentId);
  toast('コメントを削除しました');
  rerender();
}

document.addEventListener('click', async (e) => {
  /* 開いているメニュー（…）は、外を押したら閉じる */
  $$('details.menu[open]').forEach((d) => { if (!d.contains(e.target)) d.removeAttribute('open'); });

  /* プロフィールの編集中に、保存せずに別の画面へ移ろうとしたら確かめる（profile.md 3.2） */
  const link = e.target.closest('a[href^="#"]');
  if (link && location.hash === '#/settings/profile' && profileDirty()) {
    e.preventDefault();
    if (await confirmDialog({ message: '編集内容を破棄しますか？', ok: '破棄する' })) {
      profileDraft = null;
      location.hash = link.getAttribute('href');
    }
    return;
  }

  const el = e.target.closest('[data-action]');
  if (!el) {
    /* 投稿の空いているところを押したら、投稿の詳細へ */
    const card = e.target.closest('.post[data-href]');
    if (card && !e.target.closest('a, button, video, details, input, textarea, label')) location.hash = card.dataset.href;
    return;
  }
  const { action } = el.dataset;
  const id = Number(el.dataset.id || el.dataset.postId);
  switch (action) {
    case 'tab':
      ui.tab = el.dataset.tab;
      writeStorage('rt-tab', ui.tab);
      ui.listLimit = LIMITS.pageSize;
      render();
      break;
    case 'compose': openComposeModal(); break;
    case 'edit-post': el.closest('details')?.removeAttribute('open'); openComposeModal(postById(id)); break;
    case 'delete-post': el.closest('details')?.removeAttribute('open'); deletePost(id); break;
    case 'close-modal': closeModal(); break;
    case 'like': {
      const i = db.likes.findIndex((l) => l.postId === id && l.userId === ui.me);
      if (i >= 0) db.likes.splice(i, 1);
      else db.likes.push({ postId: id, userId: ui.me, createdAt: Date.now() });
      refreshPost(id);
      const btn = $(`.post[data-post-id="${id}"] [data-action="like"]`);
      if (btn) btn.focus();
      break;
    }
    case 'like-comment': {
      const i = db.commentLikes.findIndex((l) => l.commentId === id && l.userId === ui.me);
      if (i >= 0) db.commentLikes.splice(i, 1);
      else db.commentLikes.push({ commentId: id, userId: ui.me, createdAt: Date.now() });
      rerender();
      $(`#comment-${id} [data-action="like-comment"]`)?.focus();
      break;
    }
    case 'likers': openLikers(el.dataset.kind, id); break;
    case 'follow': toggleFollow(Number(el.dataset.followUser)); break;
    case 'remove-media': {
      const c = ui.composers[el.dataset.composerId];
      const [removed] = c.media.splice(Number(el.dataset.index), 1);
      if (removed && removed.src.startsWith('blob:') && !db.posts.some((p) => p.media.some((m) => m.src === removed.src))) URL.revokeObjectURL(removed.src);
      renderComposerMedia(el.dataset.composerId);
      updateComposer(el.dataset.composerId);
      composerError(el.dataset.composerId, '');
      break;
    }
    case 'lightbox': openLightbox(id, Number(el.dataset.index)); break;
    case 'lightbox-move': {
      const n = postById(lightboxState.postId).media.length;
      lightboxState.index = (lightboxState.index + Number(el.dataset.step) + n) % n;
      drawLightbox();
      break;
    }
    case 'close-lightbox': lightbox.close(); break;
    case 'focus-comment': e.preventDefault(); $('#comment-text')?.focus(); break;
    case 'toggle-thread':
      if (ui.expanded.has(id)) ui.expanded.delete(id); else ui.expanded.add(id);
      rerender();
      $(`[data-action="toggle-thread"][data-id="${id}"]`)?.focus();
      break;
    case 'reply': {
      if (ui.replyTo && ui.replyTo !== id && $('#reply-text')?.value.trim()) {
        if (!(await confirmDialog({ message: '編集内容を破棄しますか？', ok: '破棄する' }))) return;
      }
      ui.replyTo = ui.replyTo === id ? null : id;
      rerender();
      break;
    }
    case 'cancel-reply':
      if ($('#reply-text')?.value.trim() && !(await confirmDialog({ message: '編集内容を破棄しますか？', ok: '破棄する' }))) return;
      ui.replyTo = null;
      rerender();
      break;
    case 'delete-comment': deleteComment(id); break;
    case 'more-comments': ui.commentLimit += LIMITS.commentPage; rerender(); break;
    case 'back':
      if (location.hash === '#/settings/profile' && profileDirty()) {
        if (!(await confirmDialog({ message: '編集内容を破棄しますか？', ok: '破棄する' }))) return;
      }
      if (history.length > 1) history.back(); else location.hash = '#/';
      break;
    case 'remove-icon':
      profileDraft.icon = null;
      $('#icon-preview').innerHTML = avatar({ ...me(), icon: null }, 'xl');
      break;
    case 'logout':
      ui.me = null;
      location.hash = '#/login';
      toast('ログアウトしました');
      break;
    case 'fill-demo':
      $('#login-email').value = 'demo@example.com';
      $('#login-password').value = 'password123';
      $('#login-email').focus();
      break;
    default: break;
  }
});

/* 入力（文字数・検索） */
document.addEventListener('input', (e) => {
  const t = e.target;
  if (t.matches('[data-counter-id]')) {
    const id = t.dataset.counterId;
    if (ui.composers[id]) { updateComposer(id); composerError(id, ''); return; }
    updateCounter(id, t.value, LIMITS.content);
    const len = countChars(t.value.trim());
    const submit = $(`[data-submit-for="${id}"]`);
    if (submit) submit.disabled = len === 0 || len > LIMITS.content;
    const err = $(`#${id}-error`);
    if (err) err.textContent = len > LIMITS.content ? '280文字以内で入力してください' : '';
  }
  if (t.matches('[data-count-max]')) updateFieldCount(t);
  /* 日本語の変換中は検索しない */
  if (t.id === 'search-input' && !e.isComposing) scheduleSearch(t.value);
});
document.addEventListener('compositionend', (e) => {
  if (e.target.id === 'search-input') scheduleSearch(e.target.value);
});
document.addEventListener('keydown', (e) => {
  if (e.target.id === 'search-input' && e.key === 'Enter' && !e.isComposing) { e.preventDefault(); scheduleSearch(e.target.value, 0); }
});

/* ファイルを選んだとき */
document.addEventListener('change', (e) => {
  const t = e.target;
  if (t.dataset.composerFile) {
    const files = [...t.files];
    if (t.dataset.composerFile === 'image') addImages(t.dataset.composerId, files);
    else if (files[0]) addVideo(t.dataset.composerId, files[0]);
    t.value = '';
  }
  if (t.id === 'icon-input' && t.files[0]) {
    const file = t.files[0];
    const err = $('#icon-error');
    err.textContent = '';
    if (!ICON_TYPES.includes(file.type)) err.textContent = 'JPEG・PNG・WebP の画像を選んでください';
    else if (file.size > LIMITS.imageBytes) err.textContent = '画像は5MBまでです';
    else {
      profileDraft.icon = URL.createObjectURL(file);
      $('#icon-preview').innerHTML = avatar({ ...me(), icon: profileDraft.icon }, 'xl');
    }
    t.value = '';
  }
});

/* フォームの送信 */
document.addEventListener('submit', (e) => {
  const form = e.target;
  if (form.method === 'dialog') return;
  e.preventDefault();
  if (form.id === 'login-form') submitLogin();
  else if (form.id === 'signup-form') submitSignup();
  else if (form.id === 'profile-form') saveProfile();
  else if (form.dataset.composer) submitComposer(form.dataset.composer);
  else if (form.dataset.commentForm) {
    if (addComment(Number(form.dataset.commentForm), null, $('#comment-text').value, 'comment-error')) {
      ui.keepScroll = true;
      render();
      const items = $$('.comments > .comment-thread');
      items[items.length - 1]?.scrollIntoView({ block: 'center' });
    }
  } else if (form.dataset.replyForm) {
    const parent = commentById(Number(form.dataset.replyForm));
    if (!parent) { toast('返信先のコメントは削除されました'); ui.replyTo = null; rerender(); return; }
    if (addComment(parent.postId, parent.id, $('#reply-text').value, 'reply-error')) {
      ui.replyTo = null;
      rerender();
    }
  }
});

/* 最初の表示 */
render();
