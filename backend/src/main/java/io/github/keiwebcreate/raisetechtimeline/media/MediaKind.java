package io.github.keiwebcreate.raisetechtimeline.media;

import java.util.Locale;

/**
 * 画像か動画か（post_media.media_type）。S3 のキーの拡張子はサーバーが決めるので、拡張子で見分けられる。
 */
public enum MediaKind {

	/** 画像。サーバーがすべて WebP にして保存する */
	IMAGE("webp", "image/webp"),
	/** 動画。MP4（H.264）を送られたまま保存する */
	VIDEO("mp4", "video/mp4");

	private final String extension;
	private final String contentType;

	MediaKind(String extension, String contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	public String extension() {
		return extension;
	}

	public String contentType() {
		return contentType;
	}

	/** DB・API での名前（image・video） */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** キーの拡張子から種類を決める（.mp4 なら動画、ほかは画像）。キーの形は、呼ぶ側で先に確かめておく */
	public static MediaKind fromKey(String key) {
		return key.endsWith("." + VIDEO.extension) ? VIDEO : IMAGE;
	}

}
