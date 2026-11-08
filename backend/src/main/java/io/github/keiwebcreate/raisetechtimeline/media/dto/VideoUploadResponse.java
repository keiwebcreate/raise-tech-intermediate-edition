package io.github.keiwebcreate.raisetechtimeline.media.dto;

/**
 * 動画の送り先の返事。
 *
 * @param key 一時置き場のキー。送り終えたら、投稿のときにこの値を media に入れて送る
 * @param uploadUrl 画面が動画を PUT で直接送る、期限付きの URL（Content-Type は video/mp4、大きさは申し込んだとおりにする）
 */
public record VideoUploadResponse(String key, String uploadUrl) {
}
