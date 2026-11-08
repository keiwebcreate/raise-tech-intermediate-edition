package io.github.keiwebcreate.raisetechtimeline.media;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.mp4.Mp4MetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * S3 に送られた動画が、決まりに合うかを確かめる（docs/features/post.md 3.2）。道具は metadata-extractor。
 *
 * - MP4 か（iPhone の MOV は QuickTime と見分けられるので断る）
 * - 映像が H.264 か（ブラウザで再生できる形。変換はしないので、HEVC などは断る）
 * - 長さが 2分20秒（140秒）以内か
 */
@Component
public class VideoInspector {

	/** 1本の大きさの上限（100MB） */
	public static final long MAX_BYTES = 100L * 1024 * 1024;
	/** 長さの上限（秒） */
	static final double MAX_SECONDS = 140;

	static final String MESSAGE_TYPE = "MP4 の動画を選んでください";
	static final String MESSAGE_LIMIT = "動画は100MB・2分20秒までです";

	// H.264 を表す、MP4 の中の名前（avc1 が普通。avc3 も同じ H.264）
	private static final Set<String> H264 = Set.of("avc1", "avc3");

	/** 確かめる。だめなら FieldValidationException（field は media） */
	public void inspect(Path file) {
		if (detect(file) != FileType.Mp4) {
			throw invalid(MESSAGE_TYPE);
		}
		Metadata metadata = read(file);
		Mp4Directory movie = metadata.getFirstDirectoryOfType(Mp4Directory.class);
		Collection<Mp4VideoDirectory> videos = metadata.getDirectoriesOfType(Mp4VideoDirectory.class);
		if (movie == null || !movie.containsTag(Mp4Directory.TAG_DURATION_SECONDS) || videos.isEmpty()) {
			throw invalid(MESSAGE_TYPE);
		}
		for (Mp4VideoDirectory video : videos) {
			// TAG_COMPRESSION_TYPE は表示用の名前（H.264 など）なので、MP4 の中の名前そのまま（_RAW）で見分ける
			if (!H264.contains(video.getString(Mp4VideoDirectory.TAG_COMPRESSION_TYPE_RAW))) {
				throw invalid(MESSAGE_TYPE);
			}
		}
		Double seconds = movie.getDoubleObject(Mp4Directory.TAG_DURATION_SECONDS);
		if (seconds == null || seconds > MAX_SECONDS) {
			throw invalid(MESSAGE_LIMIT);
		}
	}

	private static FileType detect(Path file) {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			return FileTypeDetector.detectFileType(in);
		} catch (IOException e) {
			throw invalid(MESSAGE_TYPE);
		}
	}

	private static Metadata read(Path file) {
		try {
			return Mp4MetadataReader.readMetadata(file.toFile());
		} catch (IOException | RuntimeException e) {
			// 壊れた MP4 など
			throw invalid(MESSAGE_TYPE);
		}
	}

	private static FieldValidationException invalid(String message) {
		return new FieldValidationException("media", message);
	}

}
