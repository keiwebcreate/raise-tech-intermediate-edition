package io.github.keiwebcreate.raisetechtimeline.media.dto;

/**
 * 画像を一時置き場に置いた返事。
 *
 * @param key 一時置き場のキー。投稿のときに、この値を media に入れて送る
 * @param url 選んだ画像を投稿の前に小さく表示するための、期限付きの URL（縮小・WebP にしたあとのもの）
 */
public record ImageUploadResponse(String key, String url) {
}
