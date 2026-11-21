package io.github.keiwebcreate.raisetechtimeline.common.config;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * エラーの返事の形（Problem Details）。API 仕様書に載せるためだけのもの（SwaggerConfig）。
 * ApiExceptionHandler が作る JSON と同じ形。
 *
 * @param type エラーの種類の URI（ふつうは about:blank）
 * @param title HTTP の状態の名前（例：Bad Request）
 * @param status HTTP の状態の番号
 * @param detail 画面に出す文言（日本語）
 * @param code 画面が見分けるための名前（例：VALIDATION_ERROR・POST_NOT_FOUND）
 * @param instance 呼ばれた URL のパス
 * @param errors 入力の誤り（VALIDATION_ERROR のときだけ）
 */
@Schema(name = "Problem", description = "エラーの返事（Problem Details）")
record ProblemDoc(
		@Schema(example = "about:blank") String type,
		@Schema(example = "Bad Request") String title,
		@Schema(example = "400") int status,
		@Schema(description = "画面に出す文言", example = "入力内容に誤りがあります") String detail,
		@Schema(description = "画面が見分けるための名前", example = "VALIDATION_ERROR") String code,
		@Schema(example = "/api/posts") String instance,
		@Schema(description = "入力の誤り（VALIDATION_ERROR のときだけ）") @Nullable List<FieldError> errors) {

	/**
	 * 入力の誤り1つ。
	 *
	 * @param field 項目の名前（例：content）
	 * @param message 画面に出す文言
	 */
	@Schema(name = "FieldError", description = "入力の誤り1つ")
	record FieldError(
			@Schema(example = "content") String field,
			@Schema(example = "280文字以内で入力してください") String message) {
	}

}
