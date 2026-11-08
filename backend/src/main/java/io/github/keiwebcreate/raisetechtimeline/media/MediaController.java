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
import jakarta.validation.Valid;

/**
 * 画像・動画を選んだときの API（docs/features/post.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL             | 中身 |
 * |---|---|
 * | POST /api/media/images  | 画像を1枚送る（multipart の file）。確かめて WebP にし、一時置き場に置く |
 * | POST /api/media/videos  | 動画を S3 へ直接送るための、期限付きの送り先 URL をもらう |
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final MediaService mediaService;

	public MediaController(MediaService mediaService) {
		this.mediaService = mediaService;
	}

	@PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImageUploadResponse> uploadImage(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadImage(loginUser.id(), file));
	}

	@PostMapping("/videos")
	ResponseEntity<VideoUploadResponse> prepareVideoUpload(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody VideoUploadRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(mediaService.prepareVideoUpload(loginUser.id(), request.size()));
	}

}
