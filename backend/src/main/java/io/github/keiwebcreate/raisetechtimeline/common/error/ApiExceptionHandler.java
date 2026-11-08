package io.github.keiwebcreate.raisetechtimeline.common.error;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 例外をまとめて受け取り、Problem Details（エラーの決まった形の JSON）に変換する（トレロ風アプリと同じ作り）。
 * Controller に try-catch を書かなくてよいように、エラーの返し方はすべてここに集める。
 * Spring Security の 401・403 も、SecurityConfig からここに回してくる。
 *
 * 返事の形：{ type, title, status, detail（画面に出す文言）, code（画面が見分けるための名前）, errors（入力の誤り） }
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final String MESSAGE_VALIDATION = "入力内容に誤りがあります";
	private static final String MESSAGE_UNAUTHENTICATED = "ログインしてください";
	private static final String MESSAGE_TOKEN_EXPIRED = "ログインの有効期限が切れました。もう一度ログインしてください";
	private static final String MESSAGE_CSRF = "通信に失敗しました。画面を再読み込みしてください";
	private static final String MESSAGE_NOT_FOUND = "ページが見つかりません";
	private static final String MESSAGE_INTERNAL = "通信に失敗しました。時間をおいて再度お試しください";
	private static final String MESSAGE_IMAGE_SIZE = "画像は1枚5MBまでです";

	private final AuthCookies authCookies;

	public ApiExceptionHandler(AuthCookies authCookies) {
		this.authCookies = authCookies;
	}

	@ExceptionHandler(EmailDuplicatedException.class)
	ResponseEntity<ProblemDetail> handleEmailDuplicated(EmailDuplicatedException e) {
		return problem(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", e.getMessage());
	}

	@ExceptionHandler(UsernameDuplicatedException.class)
	ResponseEntity<ProblemDetail> handleUsernameDuplicated(UsernameDuplicatedException e) {
		return problem(HttpStatus.CONFLICT, "USERNAME_DUPLICATED", e.getMessage());
	}

	// Bean Validation では表せない入力のエラー。@Valid の失敗と同じ形（errors 付き）にする
	@ExceptionHandler(FieldValidationException.class)
	ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException e) {
		ProblemDetail body = problemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MESSAGE_VALIDATION);
		body.setProperty("errors", List.of(Map.of("field", e.getField(), "message", e.getMessage())));
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(LoginFailedException.class)
	ResponseEntity<ProblemDetail> handleLoginFailed(LoginFailedException e) {
		return problem(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", e.getMessage());
	}

	@ExceptionHandler(PostNotFoundException.class)
	ResponseEntity<ProblemDetail> handlePostNotFound(PostNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", e.getMessage());
	}

	// 回数制限（要件 N-23）。何秒後に試せるかを Retry-After で返す
	@ExceptionHandler(RateLimitedException.class)
	ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
				.body(problemDetail(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage()));
	}

	// リフレッシュトークンが使えない。もう更新できないので、2つの Cookie を消してログイン画面へ移ってもらう
	@ExceptionHandler(RefreshTokenInvalidException.class)
	ResponseEntity<ProblemDetail> handleRefreshInvalid(RefreshTokenInvalidException e) {
		HttpHeaders headers = new HttpHeaders();
		authCookies.clear().forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(headers)
				.body(problemDetail(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID", MESSAGE_TOKEN_EXPIRED));
	}

	// ログインしていない（Spring Security の AuthenticationEntryPoint から回ってくる）。
	// アクセストークンの期限切れなら TOKEN_EXPIRED にして、画面に「更新すればよい」と伝える
	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException e, HttpServletRequest request) {
		if (Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.TOKEN_EXPIRED_ATTRIBUTE))) {
			return problem(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", MESSAGE_TOKEN_EXPIRED);
		}
		return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", MESSAGE_UNAUTHENTICATED);
	}

	// CSRF トークンがない・合わない（Spring Security の AccessDeniedHandler から回ってくる）
	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> handleCsrf(CsrfException e) {
		return problem(HttpStatus.FORBIDDEN, "CSRF_INVALID", MESSAGE_CSRF);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
		return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", HttpStatus.FORBIDDEN.getReasonPhrase());
	}

	// 上のどれでもなく、Spring MVC の例外でもないもの（プログラムの不具合など）
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleOthers(Exception e) {
		// 詳細はログにだけ出し、レスポンスには含めない（要件 N-31）
		log.error("予期しないエラー", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MESSAGE_INTERNAL);
	}

	/**
	 * 送られたファイルが、受け取れる大きさ（application.yml の spring.servlet.multipart）を超えた。
	 * Spring は 413 にするが、ほかの入力の誤りと同じ 400 の形にして、画面に「画像は1枚5MBまでです」を出す
	 */
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MESSAGE_VALIDATION);
		body.setProperty("errors", List.of(Map.of("field", "file", "message", MESSAGE_IMAGE_SIZE)));
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Spring MVC の例外（入力チェックの失敗、JSON が読めない、存在しない URL など）を Problem Details にする。
	 * ステータスは Spring が決めたものをそのまま使う。
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ProblemDetail problem;
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			problem = problemDetail(statusCode, "VALIDATION_ERROR", MESSAGE_VALIDATION);
			if (ex instanceof MethodArgumentNotValidException notValid) {
				problem.setProperty("errors", fieldErrors(notValid));
			}
		} else if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			problem = problemDetail(statusCode, "NOT_FOUND", MESSAGE_NOT_FOUND);
		} else if (statusCode.is5xxServerError()) {
			log.error("予期しないエラー", ex);
			problem = problemDetail(statusCode, "INTERNAL_ERROR", MESSAGE_INTERNAL);
		} else {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			String code = status == null ? "ERROR" : status.name();
			problem = problemDetail(statusCode, code, MESSAGE_INTERNAL);
		}
		return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
	}

	// @Valid の失敗を [{ field, message }] の形にする
	private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException e) {
		return e.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList();
	}

	private static ProblemDetail problemDetail(HttpStatusCode status, String code, String detail) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setProperty("code", code);
		return body;
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
		return ResponseEntity.status(status).body(problemDetail(status, code, detail));
	}

}
