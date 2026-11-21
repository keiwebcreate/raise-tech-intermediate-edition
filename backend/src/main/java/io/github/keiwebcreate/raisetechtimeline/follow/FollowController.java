package io.github.keiwebcreate.raisetechtimeline.follow;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowResponse;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowUsersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * フォローの API（docs/features/profile.md 8章）。すべてログインが必要。相手はユーザー名で指す（プロフィールと同じ）。
 *
 * | メソッド・URL                                  | 中身 |
 * |---|---|
 * | PUT    /api/users/{ユーザー名}/follow            | フォローする（何回送っても同じ結果） |
 * | DELETE /api/users/{ユーザー名}/follow            | フォローを解除する |
 * | GET    /api/users/{ユーザー名}/following?before= | その人がフォローしている人（新しい順に20件） |
 * | GET    /api/users/{ユーザー名}/followers?before= | その人のフォロワー（新しい順に20件） |
 */
@Tag(name = "フォロー", description = "フォロー・解除と一覧（docs/features/profile.md 8章）")
@RestController
@RequestMapping("/api/users/{username}")
public class FollowController {

	private final FollowService followService;

	public FollowController(FollowService followService) {
		this.followService = followService;
	}

	// PUT・DELETE：「フォローしている状態にする／していない状態にする」。何回送っても結果が同じなので、連打や送り直しで数がずれない
	@Operation(summary = "フォローする")
	@ApiResponse(responseCode = "200", description = "フォローした。もうフォローしていても同じ返事")
	@ApiResponse(responseCode = "400", description = "自分自身（CANNOT_FOLLOW_SELF）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@PutMapping("/follow")
	FollowResponse follow(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username) {
		return followService.follow(loginUser.id(), username);
	}

	@Operation(summary = "フォローを解除する")
	@ApiResponse(responseCode = "200", description = "解除した。フォローしていなくても同じ返事")
	@ApiResponse(responseCode = "400", description = "自分自身（CANNOT_FOLLOW_SELF）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@DeleteMapping("/follow")
	FollowResponse unfollow(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username) {
		return followService.unfollow(loginUser.id(), username);
	}

	@Operation(summary = "フォロー中の人")
	@ApiResponse(responseCode = "200", description = "新しくフォローした順に20件。続きは before に nextBefore")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@GetMapping("/following")
	FollowUsersResponse following(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username,
			@RequestParam(required = false) @Nullable Long before) {
		return followService.following(loginUser.id(), username, before);
	}

	@Operation(summary = "フォロワー")
	@ApiResponse(responseCode = "200", description = "新しくフォローされた順に20件。続きは before に nextBefore")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@GetMapping("/followers")
	FollowUsersResponse followers(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username,
			@RequestParam(required = false) @Nullable Long before) {
		return followService.followers(loginUser.id(), username, before);
	}

}
