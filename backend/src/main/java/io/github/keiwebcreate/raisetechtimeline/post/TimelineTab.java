package io.github.keiwebcreate.raisetechtimeline.post;

import java.util.Locale;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/** タイムラインのタブ（docs/features/timeline.md 1章） */
public enum TimelineTab {

	/** すべて：全員の投稿 */
	ALL,
	/** フォロー中：自分と、自分がフォローしている人の投稿 */
	FOLLOWING;

	/** URL の ?tab=all・?tab=following を読む。ほかの値は入力の誤り（400） */
	public static TimelineTab parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new FieldValidationException("tab", "タブは all か following を指定してください");
		}
	}

}
