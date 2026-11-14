package io.github.keiwebcreate.raisetechtimeline.comment;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentResponse;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentsResponse;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.RepliesResponse;
import io.github.keiwebcreate.raisetechtimeline.common.error.CommentNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.common.validation.ContentText;

/**
 * コメントと返信（docs/features/comment.md）。自分の投稿にも、他の人の投稿にもコメントできる。
 * 削除は「本人のコメントか」を SQL の WHERE で確かめ、他人のものは「見つかりません」にする（要件 N-24）。
 * コメントの編集はない。
 */
@Service
public class CommentService {

	/** 投稿へのコメントを1回に読む件数 */
	public static final int PAGE_SIZE = 50;

	private static final String MESSAGE_EMPTY = "コメントを入力してください";

	private final CommentMapper commentMapper;

	public CommentService(CommentMapper commentMapper) {
		this.commentMapper = commentMapper;
	}

	/**
	 * 投稿への直接のコメント。古い順に50件。
	 *
	 * @param after 続きを読むとき、前の返事の nextAfter
	 * @throws PostNotFoundException 投稿がない
	 */
	@Transactional(readOnly = true)
	public CommentsResponse comments(long viewerId, long postId, @Nullable Long after) {
		requirePost(postId);
		// 1件多く取って、続きがあるかを知る
		List<Comment> rows = commentMapper.findTopLevel(postId, viewerId, after, PAGE_SIZE + 1);
		List<Comment> page = rows.stream().limit(PAGE_SIZE).toList();
		Long nextAfter = rows.size() > PAGE_SIZE ? page.getLast().id() : null;
		return new CommentsResponse(toResponses(page, viewerId), nextAfter);
	}

	/**
	 * 一番上のコメントの下に並ぶ返信すべて（返信の返信も含む。古い順）。
	 *
	 * @throws CommentNotFoundException コメントがない
	 */
	@Transactional(readOnly = true)
	public RepliesResponse replies(long viewerId, long commentId) {
		if (!commentMapper.commentExists(commentId)) {
			throw new CommentNotFoundException();
		}
		return new RepliesResponse(toResponses(commentMapper.findReplies(commentId, viewerId), viewerId));
	}

	/**
	 * コメントする（parentCommentId があれば返信する）。
	 *
	 * @throws PostNotFoundException 投稿がない
	 * @throws CommentNotFoundException 返信先がない・別の投稿のコメント（「返信先のコメントは削除されました」）
	 */
	@Transactional
	public CommentResponse create(long userId, long postId, String content, @Nullable Long parentCommentId) {
		String value = ContentText.normalize(content, MESSAGE_EMPTY);
		requirePost(postId);
		long commentId;
		try {
			if (parentCommentId == null) {
				commentId = commentMapper.insertComment(postId, userId, value);
			} else {
				Long replyId = commentMapper.insertReply(postId, userId, parentCommentId, value);
				if (replyId == null) {
					throw new CommentNotFoundException(CommentNotFoundException.MESSAGE_PARENT_DELETED);
				}
				commentId = replyId;
			}
		} catch (DataIntegrityViolationException e) {
			// 確かめた直後に、投稿か返信先が消された（外部キーの違反）。500 ではなく「見つかりません」にする
			if (parentCommentId == null) {
				throw new PostNotFoundException();
			}
			throw new CommentNotFoundException(CommentNotFoundException.MESSAGE_PARENT_DELETED);
		}
		return CommentResponse.from(commentMapper.findById(commentId, userId).orElseThrow(), userId);
	}

	/**
	 * コメントを消す。下に付いた返信と、いいねも一緒に消える（comment.md 4.4）。
	 *
	 * @throws CommentNotFoundException コメントがない・他人のコメント
	 */
	@Transactional
	public void delete(long userId, long commentId) {
		if (commentMapper.delete(commentId, userId) == 0) {
			throw new CommentNotFoundException();
		}
	}

	private void requirePost(long postId) {
		if (!commentMapper.postExists(postId)) {
			throw new PostNotFoundException();
		}
	}

	private static List<CommentResponse> toResponses(List<Comment> comments, long viewerId) {
		return comments.stream().map(comment -> CommentResponse.from(comment, viewerId)).toList();
	}

}
