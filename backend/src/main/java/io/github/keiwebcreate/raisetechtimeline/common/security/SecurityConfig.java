package io.github.keiwebcreate.raisetechtimeline.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import io.github.keiwebcreate.raisetechtimeline.auth.JwtAuthenticationFilter;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtTokenProvider;

/**
 * Spring Security の設定（docs/features/authentication.md 7・8）。
 *
 * - 新規登録・ログイン・更新・ログアウト以外の API は、ログインが必要
 * - ログイン状態は JWT（Cookie ACCESS_TOKEN）で確かめる。サーバーはセッションを持たない（STATELESS）
 * - CSRF 対策は有効（講義は無効）。JWT を Cookie に入れていて、ブラウザが自動で送るため。
 *   Cookie XSRF-TOKEN でトークンを渡し、ヘッダー X-XSRF-TOKEN と照合する（トレロ風アプリと同じ spa() の設定）
 * - CORS は設定しない。開発中は Vite が /api を転送し、本番は CloudFront の同じ住所なので、別の住所からの通信がない
 * - 401・403 は ApiExceptionHandler に回して、ほかのエラーと同じ Problem Details にする
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		http
				.authorizeHttpRequests(authorize -> authorize
						// ログインしていなくても呼べる API。ログアウトは、期限が切れていても Cookie を消せるように
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh",
								"/api/auth/logout")
						.permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.spa())
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				// 講義と同じく、ユーザー名・パスワードの確認の前の位置で JWT を確かめる
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
						UsernamePasswordAuthenticationFilter.class)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// ログインの画面・URL は自分で作るので、Spring Security が用意するものは使わない
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				// API の返事は JSON だけなので、何も読み込ませず、ほかのサイトに埋め込ませない
				.headers(headers -> headers.contentSecurityPolicy(csp -> csp
						.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
				.exceptionHandling(exception -> exception
						// 未ログイン → 401、CSRF 違反など → 403 を、ApiExceptionHandler で JSON にする
						.authenticationEntryPoint((request, response, e) -> exceptionResolver
								.resolveException(request, response, null, e))
						.accessDeniedHandler((request, response, e) -> exceptionResolver
								.resolveException(request, response, null, e)));
		return http.build();
	}

	/** パスワードは BCrypt でハッシュ化して保存・照合する（要件 N-22） */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
