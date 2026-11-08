package io.github.keiwebcreate.raisetechtimeline.post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.media.MediaKind;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;
import io.github.keiwebcreate.raisetechtimeline.post.dto.NewPostCountResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.PostResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;

/**
 * 投稿（docs/features/post.md）とタイムライン（docs/features/timeline.md）。
 * 編集・削除は「本人の投稿か」を SQL の WHERE で確かめ、他人の投稿は「見つかりません」にする（要件 N-24）。
 * 画像・動画のファイルは S3 に置き、DB（post_media）にはキーだけ持つ。
 */
@Service
public class PostService {

	/** タイムラインを1回に読む件数（要件 N-13） */
	public static final int PAGE_SIZE = 20;
	/** 「新しい投稿 N件」を数える上限 */
	public static final int NEW_COUNT_MAX = 100;
	/** 本文の文字数の上限（要件 2章） */
	private static final int CONTENT_MAX = 280;
	/** 画像の枚数の上限 */
	private static final int IMAGE_MAX = 4;

	private static final String MESSAGE_IMAGE_MAX = "画像は4枚までです";
	private static final String MESSAGE_MIXED = "画像と動画は一緒に投稿できません";
	private static final String MESSAGE_VIDEO_MAX = "動画は1本までです";

	private final PostMapper postMapper;
	private final PostMediaMapper postMediaMapper;
	private final MediaService mediaService;

	public PostService(PostMapper postMapper, PostMediaMapper postMediaMapper, MediaService mediaService) {
		this.postMapper = postMapper;
		this.postMediaMapper = postMediaMapper;
		this.mediaService = mediaService;
	}

	/**
	 * タイムライン。before と after は同時には使えない。
	 *
	 * @param before この投稿より古いもの（続きを読むとき）
	 * @param after この投稿より新しいもの（「新しい投稿 N件」を押したとき）。多すぎるときは最新の20件と hasMore=true
	 */
	@Transactional(readOnly = true)
	public TimelineResponse timeline(long viewerId, TimelineTab tab, @Nullable Long before, @Nullable Long after) {
		if (before != null && after != null) {
			throw new FieldValidationException("before", "before と after は同時に指定できません");
		}
		// 1件多く取って、続きがあるかを知る
		List<Post> rows = postMapper.findTimeline(viewerId, tab == TimelineTab.FOLLOWING, before, after,
				PAGE_SIZE + 1);
		boolean hasMore = rows.size() > PAGE_SIZE;
		return new TimelineResponse(toResponses(rows.stream().limit(PAGE_SIZE).toList(), viewerId), hasMore);
	}

	@Transactional(readOnly = true)
	public NewPostCountResponse countNewer(long viewerId, TimelineTab tab, @Nullable Long after) {
		return new NewPostCountResponse(
				postMapper.countNewer(viewerId, tab == TimelineTab.FOLLOWING, after, NEW_COUNT_MAX));
	}

	@Transactional(readOnly = true)
	public PostResponse get(long viewerId, long postId) {
		Post post = postMapper.findById(postId).orElseThrow(PostNotFoundException::new);
		return toResponses(List.of(post), viewerId).getFirst();
	}

	/**
	 * 投稿する。
	 *
	 * @param media 付ける画像・動画の一時置き場のキー（並べたい順）。null・空なら付けない
	 */
	@Transactional
	public PostResponse create(long userId, String content, @Nullable List<String> media) {
		long postId = postMapper.insert(userId, normalize(content));
		if (media != null && !media.isEmpty()) {
			replaceMedia(userId, postId, media, List.of());
		}
		return get(userId, postId);
	}

	/**
	 * 本文と画像・動画を変える。並び順（投稿した日時）は変わらず、「編集済み」になる。
	 *
	 * @param media 編集のあとの画像・動画（残すものは今のキー、足すものは一時置き場のキー）。null なら今のまま
	 * @throws PostNotFoundException 投稿がない・他人の投稿
	 */
	@Transactional
	public PostResponse update(long userId, long postId, String content, @Nullable List<String> media) {
		if (postMapper.updateContent(postId, userId, normalize(content)) == 0) {
			throw new PostNotFoundException();
		}
		if (media != null) {
			replaceMedia(userId, postId, media, postMediaMapper.findByPostIds(List.of(postId)));
		}
		return get(userId, postId);
	}

	/**
	 * 投稿を消す。画像・動画の行は ON DELETE CASCADE で一緒に消え、S3 のファイルはコミットのあとに消す
	 * （コメント・いいねは、その機能を作るときに ON DELETE CASCADE で一緒に消える）。
	 *
	 * @throws PostNotFoundException 投稿がない・他人の投稿
	 */
	@Transactional
	public void delete(long userId, long postId) {
		List<String> keys = postMediaMapper.findByPostIds(List.of(postId)).stream().map(PostMedia::s3Key).toList();
		if (postMapper.delete(postId, userId) == 0) {
			throw new PostNotFoundException();
		}
		mediaService.deleteAfterCommit(keys);
	}

	/**
	 * 投稿の画像・動画を、requested のとおりにする（docs/features/post.md 3章）。
	 *
	 * 1. 数と組み合わせを確かめる（画像だけ4枚まで／動画だけ1本／混ぜない）
	 * 2. それぞれ「今付いているもの」か「自分の一時置き場のもの」かを確かめる（他人のファイルは使えない）
	 * 3. 新しいものは、S3 にあるか（動画は中身も）を確かめてから、正式な場所へコピーする
	 * 4. post_media を並べ直す。外したもの・コピー元の一時ファイルは、コミットのあとに S3 から消す
	 */
	private void replaceMedia(long userId, long postId, List<String> requested, List<PostMedia> current) {
		checkCombination(requested);
		Set<String> currentKeys = current.stream().map(PostMedia::s3Key).collect(Collectors.toSet());
		for (String key : requested) {
			if (!currentKeys.contains(key) && !mediaService.isOwnTmpKey(key, userId)) {
				throw new FieldValidationException("media", MediaService.MESSAGE_NOT_FOUND);
			}
		}
		if (requested.equals(current.stream().map(PostMedia::s3Key).toList())) {
			// 変わっていない
			return;
		}
		List<String> tmpKeys = requested.stream().filter(key -> !currentKeys.contains(key)).toList();
		tmpKeys.forEach(mediaService::checkUploaded);

		List<String> finalKeys = new ArrayList<>();
		List<String> copied = new ArrayList<>();
		// DB の保存が失敗したら、コピーしたものを消す（登録は先に。コピーの途中で失敗したときも消えるように）
		mediaService.deleteAfterRollback(copied);
		for (String key : requested) {
			if (currentKeys.contains(key)) {
				finalKeys.add(key);
			} else {
				String postKey = mediaService.copyToPosts(key);
				copied.add(postKey);
				finalKeys.add(postKey);
			}
		}
		postMediaMapper.deleteByPostId(postId);
		for (int i = 0; i < finalKeys.size(); i++) {
			String key = finalKeys.get(i);
			postMediaMapper.insert(postId, MediaKind.fromKey(key).value(), key, i + 1);
		}
		List<String> removed = new ArrayList<>(currentKeys);
		removed.removeAll(finalKeys);
		removed.addAll(tmpKeys);
		mediaService.deleteAfterCommit(removed);
	}

	private static void checkCombination(List<String> keys) {
		if (keys.stream().anyMatch(key -> key == null)) {
			throw new FieldValidationException("media", MediaService.MESSAGE_NOT_FOUND);
		}
		if (new HashSet<>(keys).size() != keys.size()) {
			// 同じものを2回
			throw new FieldValidationException("media", MediaService.MESSAGE_NOT_FOUND);
		}
		long videos = keys.stream().filter(key -> MediaKind.fromKey(key) == MediaKind.VIDEO).count();
		long images = keys.size() - videos;
		if (videos > 0 && images > 0) {
			throw new FieldValidationException("media", MESSAGE_MIXED);
		}
		if (videos > 1) {
			throw new FieldValidationException("media", MESSAGE_VIDEO_MAX);
		}
		if (images > IMAGE_MAX) {
			throw new FieldValidationException("media", MESSAGE_IMAGE_MAX);
		}
	}

	/** 投稿に画像・動画（見るための期限付き URL）を付けて返事にする。画像・動画は1回の SQL でまとめて読む */
	private List<PostResponse> toResponses(List<Post> posts, long viewerId) {
		if (posts.isEmpty()) {
			return List.of();
		}
		Map<Long, List<PostResponse.Media>> mediaByPost = postMediaMapper
				.findByPostIds(posts.stream().map(Post::id).toList()).stream()
				.collect(Collectors.groupingBy(PostMedia::postId, Collectors.mapping(
						m -> new PostResponse.Media(m.mediaType(), m.s3Key(), mediaService.viewUrl(m.s3Key())),
						Collectors.toList())));
		return posts.stream()
				.map(post -> PostResponse.from(post, viewerId,
						List.copyOf(mediaByPost.getOrDefault(post.id(), List.of()))))
				.toList();
	}

	/**
	 * 前後の空白・改行を取り除き、1〜280文字かを確かめる。
	 * 見た目の1文字を1文字と数える（絵文字も1文字。DB の varchar と同じ数え方。post.md 3.1）
	 */
	private static String normalize(String content) {
		String value = content.strip();
		if (value.isEmpty()) {
			throw new FieldValidationException("content", "本文を入力してください");
		}
		if (value.codePointCount(0, value.length()) > CONTENT_MAX) {
			throw new FieldValidationException("content", "280文字以内で入力してください");
		}
		return value;
	}

}
