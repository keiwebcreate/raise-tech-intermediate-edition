package io.github.keiwebcreate.raisetechtimeline.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * リクエストのたびに、Cookie ACCESS_TOKEN の JWT を確かめる（講義と同じ OncePerRequestFilter）。
 * 講義は Authorization ヘッダーから読むが、このアプリは HttpOnly の Cookie から読む（ユーザーの判断）。
 *
 * - 正しければ、ログインしている人（LoginUser）を Spring Security に覚えさせる
 * - ない・正しくなければ、何もしない。ログインが必要な API は、Spring Security が 401 で断る
 * - 期限切れなら、そのことを印（属性）に残す。401 の返事を TOKEN_EXPIRED にして、画面に「更新すればよい」と伝える
 *
 * Spring のコンポーネントにすると、Spring Boot がほかの場所にも登録してしまうので、SecurityConfig で new する。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/** 期限切れの印（ApiExceptionHandler が読む） */
	public static final String TOKEN_EXPIRED_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".TOKEN_EXPIRED";

	private final JwtTokenProvider tokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
		if (cookie != null && !cookie.getValue().isBlank()) {
			try {
				long userId = tokenProvider.parseUserId(cookie.getValue());
				// 引数3つのコンストラクタは「確かめ済み（ログイン済み）」の印を付ける。権限（ロール）はまだ使わない
				var authentication = new UsernamePasswordAuthenticationToken(new LoginUser(userId), null, List.of());
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			} catch (ExpiredJwtException e) {
				request.setAttribute(TOKEN_EXPIRED_ATTRIBUTE, Boolean.TRUE);
			} catch (JwtException | IllegalArgumentException e) {
				// 署名が合わない・壊れているなど。ログインしていないものとして扱う
			}
		}
		filterChain.doFilter(request, response);
	}

}
