package io.github.keiwebcreate.raisetechtimeline.post;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.post.dto.NewPostCountResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.PostResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;

/**
 * 投稿（docs/features/post.md）とタイムライン（docs/features/timeline.md）。
 * 編集・削除は「本人の投稿か」を SQL の WHERE で確かめ、他人の投稿は「見つかりません」にする（要件 N-24）。
 */
@Service
public class PostService {

	/** タイムラインを1回に読む件数（要件 N-13） */
	public static final int PAGE_SIZE = 20;
	/** 「新しい投稿 N件」を数える上限 */
	public static final int NEW_COUNT_MAX = 100;
	/** 本文の文字数の上限（要件 2章） */
	private static final int CONTENT_MAX = 280;

	private final PostMapper postMapper;

	public PostService(PostMapper postMapper) {
		this.postMapper = postMapper;
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
		List<PostResponse> posts = rows.stream()
				.limit(PAGE_SIZE)
				.map(post -> PostResponse.from(post, viewerId))
				.toList();
		return new TimelineResponse(posts, hasMore);
	}

	@Transactional(readOnly = true)
	public NewPostCountResponse countNewer(long viewerId, TimelineTab tab, @Nullable Long after) {
		return new NewPostCountResponse(
				postMapper.countNewer(viewerId, tab == TimelineTab.FOLLOWING, after, NEW_COUNT_MAX));
	}

	@Transactional(readOnly = true)
	public PostResponse get(long viewerId, long postId) {
		return PostResponse.from(postMapper.findById(postId).orElseThrow(PostNotFoundException::new), viewerId);
	}

	@Transactional
	public PostResponse create(long userId, String content) {
		long postId = postMapper.insert(userId, normalize(content));
		return get(userId, postId);
	}

	/**
	 * 本文を変える。並び順（投稿した日時）は変わらず、「編集済み」になる。
	 *
	 * @throws PostNotFoundException 投稿がない・他人の投稿
	 */
	@Transactional
	public PostResponse update(long userId, long postId, String content) {
		if (postMapper.updateContent(postId, userId, normalize(content)) == 0) {
			throw new PostNotFoundException();
		}
		return get(userId, postId);
	}

	/**
	 * 投稿を消す（コメント・いいねは、その機能を作るときに ON DELETE CASCADE で一緒に消える）。
	 *
	 * @throws PostNotFoundException 投稿がない・他人の投稿
	 */
	@Transactional
	public void delete(long userId, long postId) {
		if (postMapper.delete(postId, userId) == 0) {
			throw new PostNotFoundException();
		}
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
