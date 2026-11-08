package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * 入力のルール違反のうち、Bean Validation（@NotBlank など）では表せないもの（400 VALIDATION_ERROR）。
 * 例：表示名の文字数（絵文字も1文字と数えるため、@Size では数えられない）
 */
public class FieldValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String field;

	public FieldValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
