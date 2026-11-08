package io.github.keiwebcreate.raisetechtimeline.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 動画の送り先の申し込み。
 *
 * @param size 送る動画の大きさ（バイト）。100MB まで（MediaService で確かめる）
 */
public record VideoUploadRequest(
		@NotNull(message = "MP4 の動画を選んでください")
		@Positive(message = "MP4 の動画を選んでください")
		Long size) {
}
