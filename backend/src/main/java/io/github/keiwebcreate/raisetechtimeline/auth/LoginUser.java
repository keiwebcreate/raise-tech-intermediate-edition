package io.github.keiwebcreate.raisetechtimeline.auth;

/**
 * ログインしている人。JwtAuthenticationFilter が JWT から作り、Spring Security に覚えさせる。
 * Controller では @AuthenticationPrincipal LoginUser loginUser で受け取る。
 *
 * @param id ユーザーの ID（JWT の subject）
 */
public record LoginUser(long id) {
}
