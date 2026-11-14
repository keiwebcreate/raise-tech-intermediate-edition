package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * 自分自身をフォローしようとした（400 CANNOT_FOLLOW_SELF。docs/features/profile.md 4.1）。
 * 画面は自分にフォローボタンを出さないので、ふつうは起きない。
 */
public class CannotFollowSelfException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CannotFollowSelfException() {
		super("自分自身はフォローできません");
	}

}
