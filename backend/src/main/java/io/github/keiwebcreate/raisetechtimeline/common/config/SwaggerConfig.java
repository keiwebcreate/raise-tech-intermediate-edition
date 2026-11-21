package io.github.keiwebcreate.raisetechtimeline.common.config;

import java.util.Comparator;
import java.util.List;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.common.security.CsrfCookieFilter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API 仕様書（OpenAPI）の設定。仕様書の画面（Swagger UI）は開発中だけ開く（application-dev.yml）。
 *
 * - 開発中に http://localhost:8081/swagger-ui.html で開く。仕様書そのもの（JSON）は /v3/api-docs
 * - URL・送るもの・返事の形は、この仕様書を正とする。理由・エラーの文言・決めたことは、機能定義書の「8章 API」に書く
 * - 本番では springdoc.api-docs.enabled が false なので、このクラスは何もしない（下の @ConditionalOnProperty）
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class SwaggerConfig {

	/** 仕様書の住所（画面と JSON） */
	private static final String[] SWAGGER_PATHS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

	/** ログインの Cookie（JWT）の名前。仕様書の securitySchemes の名前にも使う */
	static final String COOKIE_AUTH = "cookieAuth";

	/** CSRF トークンのヘッダー */
	static final String CSRF_HEADER = "csrfToken";

	/** エラーの返事の形（Problem Details）の名前 */
	private static final String PROBLEM_SCHEMA = "Problem";

	private static final String PROBLEM_JSON = "application/problem+json";

	/** 機能のまとまりの並び順（Controller の @Tag の名前） */
	private static final List<String> TAG_ORDER = List.of("認証", "投稿・タイムライン", "画像・動画", "いいね", "コメント",
			"プロフィール・検索", "フォロー");

	private static final String DESCRIPTION = """
			RaiseTechtimeline（X のようなタイムライン形式の SNS）の API です。

			## ログイン
			- `POST /api/auth/login` でログインすると、Cookie `ACCESS_TOKEN`（15分）と `REFRESH_TOKEN`（7日）が付きます。\
			どちらも HttpOnly で、画面の JavaScript からは読めません
			- ほかの API は `ACCESS_TOKEN` で本人を確かめます。期限が切れたら 401 `TOKEN_EXPIRED` になるので、\
			`POST /api/auth/refresh` で取り直します

			## CSRF 対策
			- GET 以外は、ヘッダー `X-XSRF-TOKEN` に Cookie `XSRF-TOKEN` の値を入れて送ります。ないと 403 `CSRF_INVALID`
			- この画面（Swagger UI）は、開いたときに Cookie が付き、送るときに Cookie から読んで自動で付けます

			## この画面で試すとき
			画面（http://localhost:5174）でログインしていれば、同じ localhost の Cookie がそのまま使えます。\
			ここで試すなら、先に `POST /api/auth/login` を送ってください

			## エラーの返事
			すべて Problem Details（`application/problem+json`）の形です。画面に出す文言は `detail`、\
			見分けるための名前は `code` に入ります。入力の誤りは `errors` に項目ごとに入ります

			## 文書
			理由・エラーの文言・決めたことは、各機能定義書（`docs/features/*.md`）の「8章 API」にあります
			""";

	/** 仕様書の題名・説明と、ログインの方法（Cookie・CSRF トークン） */
	@Bean
	OpenAPI raiseTechtimelineOpenApi() {
		return new OpenAPI()
				.info(new Info().title("RaiseTechtimeline API").version("v1").description(DESCRIPTION))
				.components(new Components()
						.addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name(AuthCookies.ACCESS_TOKEN)
								.description("ログインの証明（JWT）。POST /api/auth/login で付く HttpOnly の Cookie"))
						.addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)
								.name("X-XSRF-TOKEN")
								.description("Cookie XSRF-TOKEN の値。GET 以外で必要")))
				// 何も書かなければ、すべての API はログインが必要（ログイン不要の API は @SecurityRequirements() で外す）
				.addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
	}

	/**
	 * すべての API に共通のエラー（401・403）と、エラーの返事の形を足す。
	 *
	 * - ログインが必要な API には 401（未ログイン・期限切れ）
	 * - GET 以外には 403（CSRF トークンがない・合わない）
	 * - Controller に書いたエラー（400・404 など）に、Problem Details の形を付ける
	 */
	@Bean
	GlobalOpenApiCustomizer commonErrorResponses() {
		return openApi -> {
			// 機能のまとまりを、使う流れの順に並べる（何もしないと、Controller を読み込んだ順でばらばらになる）
			if (openApi.getTags() != null) {
				openApi.getTags().sort(Comparator.comparingInt(tag -> TAG_ORDER.indexOf(tag.getName())));
			}
			// ProblemDoc から、Problem と FieldError の形を作って登録する
			ModelConverters.getInstance().readAll(ProblemDoc.class)
					.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
			Content problem = new Content().addMediaType(PROBLEM_JSON,
					new MediaType().schema(new Schema<Object>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
			openApi.getPaths().values().forEach(path -> path.readOperationsMap().forEach((method, operation) -> {
				ApiResponses responses = operation.getResponses();
				boolean needsLogin = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
				if (needsLogin) {
					responses.putIfAbsent("401", new ApiResponse().description(
							"ログインしていない（UNAUTHENTICATED）・ログインの期限切れ（TOKEN_EXPIRED）"));
				}
				if (method != PathItem.HttpMethod.GET) {
					responses.putIfAbsent("403", new ApiResponse().description(
							"CSRF トークンがない・合わない（CSRF_INVALID）"));
					addCsrfRequirement(operation, needsLogin);
				}
				// 4xx・5xx で形を書いていないものに、Problem Details の形を付ける
				responses.forEach((code, response) -> {
					if (!code.startsWith("2") && response.getContent() == null) {
						response.setContent(problem);
					}
				});
			}));
		};
	}

	/** GET 以外は、ログインの Cookie に加えて CSRF トークンが必要、と仕様書に書く */
	private static void addCsrfRequirement(Operation operation, boolean needsLogin) {
		SecurityRequirement requirement = new SecurityRequirement().addList(CSRF_HEADER);
		if (needsLogin) {
			requirement.addList(COOKIE_AUTH);
		}
		operation.setSecurity(List.of(requirement));
	}

	/**
	 * 仕様書の住所だけに使うセキュリティの決まり（開発中だけ。本番ではこのクラスごと動かない）。
	 *
	 * - ログインしなくても仕様書を開ける
	 * - 仕様書の画面はスクリプト・CSS・画像を読み込むので、API 用の「何も読み込ませない」CSP（SecurityConfig）ではなく、
	 *   同じ住所のものだけ読み込める CSP にする
	 * - @Order(1)：SecurityConfig の決まり（すべての URL）より先に、この住所だけを受け持つ
	 */
	@Bean
	@Order(1)
	SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) {
		http
				.securityMatcher(SWAGGER_PATHS)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, SWAGGER_PATHS).permitAll()
						.anyRequest().denyAll())
				// 仕様書の画面を開いた時点で Cookie XSRF-TOKEN を付ける（画面から最初の POST をそのまま試せるように）。
				// この住所は GET だけなので、CSRF の照合で断られることはない
				.csrf(csrf -> csrf.spa())
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
						"default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
								+ "frame-ancestors 'none'")));
		return http.build();
	}

}
