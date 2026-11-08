package io.github.keiwebcreate.raisetechtimeline.media;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import net.coobird.thumbnailator.Thumbnails;

/**
 * 送られた画像を確かめて、保存する形（WebP）にする（docs/features/post.md 3.2）。
 *
 * 1. 中身から形式を見分ける（ファイル名・Content-Type は利用者が自由に変えられるので信用しない。要件 N-28）
 * 2. 縦横の大きさを先に読み、大きすぎるものは展開する前に断る（小さなファイルで巨大な画像を作り、メモリを使い切らせる攻撃を防ぐ）
 * 3. 写真の向き（Exif の Orientation）を見て回し、横 1600px 以下に縮小する。小さい画像は大きくしない
 * 4. WebP で書き直す。書き直すので、位置情報などの Exif は残らない。GIF は1枚目だけになる
 */
@Component
public class ImageProcessor {

	/** 1枚の大きさの上限（5MB） */
	public static final long MAX_BYTES = 5L * 1024 * 1024;
	/** 横幅の目安 */
	static final int MAX_WIDTH = 1600;
	/** 縦×横の上限（6000万画素。スマートフォンの写真は大きくても5000万画素ほどなので、少し余裕を見る） */
	static final long MAX_PIXELS = 60_000_000L;
	/** WebP の画質（0〜1）。写真で見た目がほとんど変わらず、大きさが小さくなる値 */
	private static final float QUALITY = 0.8f;

	static final String MESSAGE_TYPE = "JPEG・PNG・WebP・GIF の画像を選んでください";
	static final String MESSAGE_SIZE = "画像は1枚5MBまでです";
	static final String MESSAGE_PIXELS = "画像の縦横が大きすぎます";

	// ImageIO の形式の名前（小文字にしたもの）
	private static final Set<String> ACCEPTED = Set.of("jpeg", "png", "webp", "gif");

	public ImageProcessor() {
		// WebP の読み書きの道具（jar の中の部品）を ImageIO に登録し直す。
		// Spring Boot の1つの jar で動かすとき、起動の順番によっては自動で見つからないことがあるため
		ImageIO.scanForPlugins();
	}

	/** 確かめて、WebP にしたデータを返す。だめなら FieldValidationException（field は file） */
	public byte[] toWebp(byte[] data) {
		if (data.length == 0) {
			throw invalid(MESSAGE_TYPE);
		}
		if (data.length > MAX_BYTES) {
			throw invalid(MESSAGE_SIZE);
		}
		checkFormatAndPixels(data);
		return encode(resize(decode(data)));
	}

	private static void checkFormatAndPixels(byte[] data) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw invalid(MESSAGE_TYPE);
			}
			ImageReader reader = readers.next();
			try {
				if (!ACCEPTED.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw invalid(MESSAGE_TYPE);
				}
				reader.setInput(in, true, true);
				// 縦横は、画像の先頭の情報だけを読んで分かる（まだ展開しない）
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw invalid(MESSAGE_PIXELS);
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof FieldValidationException validation) {
				throw validation;
			}
			// 壊れた画像など
			throw invalid(MESSAGE_TYPE);
		}
	}

	// 展開して、写真の向きを直す（Thumbnailator が Exif の Orientation を読んで回す）
	private static BufferedImage decode(byte[] data) {
		try {
			return Thumbnails.of(new ByteArrayInputStream(data)).scale(1.0).useExifOrientation(true)
					.asBufferedImage();
		} catch (IOException | RuntimeException e) {
			throw invalid(MESSAGE_TYPE);
		}
	}

	private static BufferedImage resize(BufferedImage image) {
		BufferedImage resized = image;
		if (image.getWidth() > MAX_WIDTH) {
			try {
				resized = Thumbnails.of(image).width(MAX_WIDTH).asBufferedImage();
			} catch (IOException e) {
				throw new IllegalStateException("画像を縮小できませんでした", e);
			}
		}
		// WebP に書く道具が扱える形（透明あり ARGB／なし RGB）にそろえる。GIF・白黒の画像などは別の形で読まれるため
		int type = resized.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		if (resized.getType() == type) {
			return resized;
		}
		BufferedImage converted = new BufferedImage(resized.getWidth(), resized.getHeight(), type);
		Graphics2D g = converted.createGraphics();
		try {
			g.drawImage(resized, 0, 0, null);
		} finally {
			g.dispose();
		}
		return converted;
	}

	private static byte[] encode(BufferedImage image) {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
		if (!writers.hasNext()) {
			throw new IllegalStateException("WebP を書く道具が見つかりません");
		}
		ImageWriter writer = writers.next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			// 非可逆（写真向き。大きさが小さくなる）の圧縮で、画質を決める
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			if (param instanceof WebPWriteParam webp) {
				webp.setCompressionType(CompressionType.Lossy);
			}
			param.setCompressionQuality(QUALITY);
			writer.setOutput(ios);
			// 2つめの引数（metadata）を null にするので、Exif などは書かれない
			writer.write(null, new IIOImage(image, null, null), param);
		} catch (IOException e) {
			throw new IllegalStateException("WebP に変換できませんでした", e);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static FieldValidationException invalid(String message) {
		return new FieldValidationException("file", message);
	}

}
