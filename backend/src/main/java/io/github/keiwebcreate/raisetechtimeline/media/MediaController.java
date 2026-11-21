package io.github.keiwebcreate.raisetechtimeline.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.media.dto.ImageUploadResponse;
import io.github.keiwebcreate.raisetechtimeline.media.dto.VideoUploadRequest;
import io.github.keiwebcreate.raisetechtimeline.media.dto.VideoUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 画像・動画を選んだときの API（docs/features/post.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL             | 中身 |
 * |---|---|
 * | POST /api/media/images  | 画像を1枚送る（multipart の file）。確かめて WebP にし、一時置き場に置く |
 * | POST /api/media/icons   | アイコンを1枚送る（multipart の file）。確かめて正方形の WebP にし、一時置き場に置く |
 * | POST /api/media/videos  | 動画を S3 へ直接送るための、期限付きの送り先 URL をもらう |
 */
@Tag(name = "画像・動画", description = "投稿の画像・動画とアイコンを送る（docs/features/post.md・profile.md 8章）")
@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final MediaService mediaService;

	public MediaController(MediaService mediaService) {
		this.mediaService = mediaService;
	}

	@Operation(summary = "投稿の画像を送る", description = "1枚ずつ。中身が画像かを確かめ、WebP にする")
	@ApiResponse(responseCode = "201", description = "一時置き場に置いた。key を投稿の media に入れる")
	@ApiResponse(responseCode = "400",
			description = "JPEG・PNG・WebP・GIF でない、5MB を超えた（VALIDATION_ERROR。errors[].field は file）")
	@PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImageUploadResponse> uploadImage(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadImage(loginUser.id(), file));
	}

	// アイコン（docs/features/profile.md 3.2）。正方形の 400×400 にして一時置き場に置く。保存はプロフィールの編集で
	@Operation(summary = "アイコンを送る", description = "真ん中を正方形に切り取り、400×400 の WebP にする")
	@ApiResponse(responseCode = "201", description = "一時置き場に置いた。key をプロフィールの iconKey に入れる")
	@ApiResponse(responseCode = "400",
			description = "JPEG・PNG・WebP でない、5MB を超えた（VALIDATION_ERROR。errors[].field は file）")
	@PostMapping(path = "/icons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImageUploadResponse> uploadIcon(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadIcon(loginUser.id(), file));
	}

	@Operation(summary = "動画の送り先をもらう", description = "動画はサーバーを通さず、ブラウザから S3 へ直接送る")
	@ApiResponse(responseCode = "201", description = "uploadUrl へ PUT（Content-Type: video/mp4）で動画を直接送る。期限15分")
	@ApiResponse(responseCode = "400", description = "100MB を超えた（VALIDATION_ERROR。errors[].field は size）")
	@PostMapping("/videos")
	ResponseEntity<VideoUploadResponse> prepareVideoUpload(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody VideoUploadRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(mediaService.prepareVideoUpload(loginUser.id(), request.size()));
	}

}
