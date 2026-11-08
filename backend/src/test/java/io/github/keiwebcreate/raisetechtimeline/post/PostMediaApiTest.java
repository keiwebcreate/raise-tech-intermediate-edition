package io.github.keiwebcreate.raisetechtimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 画像・動画の投稿のテスト（docs/features/post.md 3章・4章・5章）。
 * 本物の PostgreSQL 18 と S3Mock（どちらも Testcontainers）を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostMediaApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private S3Client s3;

	private final HttpClient http = HttpClient.newHttpClient();

	// ===== 画像を送る（POST /api/media/images） =====

	@Test
	void 位置情報と向きの入ったJPEGは_向きを直して縮小し_Exifのない_WebPで一時置き場に置かれる() throws Exception {
		long me = newUser();
		// 横 2400 × 縦 1200 の写真。Exif の Orientation=6（右に90度回して見る）と、位置情報を入れる
		byte[] jpeg = withExif(jpeg(2400, 1200));

		String body = uploadImage(me, jpeg, "photo.jpg", "image/jpeg")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		assertThat(key).matches("tmp/" + me + "/[0-9a-f-]{36}\\.webp");

		byte[] saved = s3Object(key);
		// WebP（先頭が RIFF....WEBP）で、Exif も位置情報も残っていない
		assertThat(new String(saved, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
		assertThat(new String(saved, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
		assertThat(contains(saved, "Exif")).isFalse();
		assertThat(contains(saved, "EXIF")).isFalse();
		// 向きを直したので縦長（横 1200 × 縦 2400）になる。横は 1600px 以下なので縮小しない
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(saved));
		assertThat(image.getWidth()).isEqualTo(1200);
		assertThat(image.getHeight()).isEqualTo(2400);

		// 返事の url（期限付き URL）で、S3 から実際に読める
		HttpResponse<byte[]> view = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.url"))).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(view.statusCode()).isEqualTo(200);
		assertThat(view.body()).isEqualTo(saved);
	}

	@Test
	void 横1600pxより大きい画像は横1600pxに縮小され_小さい画像は大きくしない() throws Exception {
		long me = newUser();
		BufferedImage large = webpImage(uploadedKey(me, jpeg(2000, 1000)));
		assertThat(large.getWidth()).isEqualTo(1600);
		assertThat(large.getHeight()).isEqualTo(800);

		BufferedImage small = webpImage(uploadedKey(me, jpeg(300, 200)));
		assertThat(small.getWidth()).isEqualTo(300);
	}

	@Test
	void PNG_GIF_WebPも受け付ける() throws Exception {
		long me = newUser();
		uploadImage(me, encode(picture(400, 300, BufferedImage.TYPE_INT_ARGB), "png"), "a.png", "image/png")
				.andExpect(status().isCreated());
		uploadImage(me, encode(picture(400, 300, BufferedImage.TYPE_INT_RGB), "gif"), "a.gif", "image/gif")
				.andExpect(status().isCreated());
		uploadImage(me, encode(picture(400, 300, BufferedImage.TYPE_INT_RGB), "webp"), "a.webp", "image/webp")
				.andExpect(status().isCreated());
	}

	@Test
	void 動くGIFは1枚目だけになる() throws Exception {
		long me = newUser();
		// 1枚目が赤、2枚目が緑の GIF
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.prepareWriteSequence(null);
			writer.writeToSequence(new IIOImage(filled(40, 30, Color.RED), null, null), null);
			writer.writeToSequence(new IIOImage(filled(40, 30, Color.GREEN), null, null), null);
			writer.endWriteSequence();
		} finally {
			writer.dispose();
		}

		// GIF は「色の表」を持つ形で読まれる。作り直すときに色が変わらない（赤が黒にならない）ことも確かめる
		BufferedImage saved = webpImage(uploadedKey(me, out.toByteArray(), "anime.gif", "image/gif"));
		Color center = new Color(saved.getRGB(20, 15));
		assertThat(center.getRed()).isGreaterThan(200);
		assertThat(center.getGreen()).isLessThan(60);
	}

	@Test
	void 透明なPNGは透明さが残る() throws Exception {
		long me = newUser();
		// 左半分が透明、右半分が青
		BufferedImage png = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = png.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(20, 0, 20, 20);
		g.dispose();

		BufferedImage saved = webpImage(uploadedKey(me, encode(png, "png"), "clear.png", "image/png"));
		assertThat(saved.getColorModel().hasAlpha()).isTrue();
		assertThat(saved.getRGB(5, 10) >>> 24).as("透明な部分").isLessThan(20);
		assertThat(saved.getRGB(30, 10) >>> 24).as("青の部分").isGreaterThan(230);
	}

	@Test
	void 画像でないファイルは_名前や種類を画像にしても断る() throws Exception {
		long me = newUser();
		expectFileError(uploadImage(me, "これは画像ではありません".getBytes(StandardCharsets.UTF_8), "fake.jpg",
				"image/jpeg"), "JPEG・PNG・WebP・GIF の画像を選んでください");
		// BMP は画像だが、受け付ける形式ではない
		expectFileError(uploadImage(me, encode(picture(10, 10, BufferedImage.TYPE_INT_RGB), "bmp"), "a.jpg",
				"image/jpeg"), "JPEG・PNG・WebP・GIF の画像を選んでください");
	}

	@Test
	void 画像は5MBを超えると断る() throws Exception {
		long me = newUser();
		// 5MB を少し超える（Spring が受け取り、コードで断る）
		byte[] overLimit = Arrays.copyOf(jpeg(100, 100), 5 * 1024 * 1024 + 1);
		expectFileError(uploadImage(me, overLimit, "big.jpg", "image/jpeg"), "画像は1枚5MBまでです");
		// 受け取れる大きさ（6MB）も超える（Spring が断る）
		byte[] tooLarge = new byte[7 * 1024 * 1024];
		expectFileError(uploadImage(me, tooLarge, "big.jpg", "image/jpeg"), "画像は1枚5MBまでです");
	}

	@Test
	void 縦横が大きすぎる画像は展開する前に断る() throws Exception {
		long me = newUser();
		// 10000 × 7000（7000万画素）。白黒2色なので PNG は小さい
		BufferedImage huge = new BufferedImage(10_000, 7_000, BufferedImage.TYPE_BYTE_BINARY);
		expectFileError(uploadImage(me, encode(huge, "png"), "huge.png", "image/png"), "画像の縦横が大きすぎます");
	}

	@Test
	void ログインしていないと画像を送れない() throws Exception {
		mockMvc.perform(multipart("/api/media/images")
				.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg(10, 10))))
				.andExpect(status().is4xxClientError());
	}

	// ===== 画像を付けて投稿する =====

	@Test
	void 画像4枚を付けて投稿でき_選んだ順に並び_一時置き場から正式な場所へ移る() throws Exception {
		long me = newUser();
		List<String> tmpKeys = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tmpKeys.add(uploadedKey(me, jpeg(100 + i, 100)));
		}

		String body = as(me, post("/api/posts"), json("画像4枚", tmpKeys))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.media.length()").value(4))
				.andExpect(jsonPath("$.media[0].type").value("image"))
				.andReturn().getResponse().getContentAsString();

		List<String> keys = JsonPath.read(body, "$.media[*].key");
		assertThat(keys).allMatch(key -> key.matches("posts/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp"));
		// 並び順は選んだ順（横幅 100・101・102・103 の順）
		for (int i = 0; i < 4; i++) {
			assertThat(webpImage(keys.get(i)).getWidth()).isEqualTo(100 + i);
		}
		// 一時置き場のものは消える
		tmpKeys.forEach(key -> assertThat(exists(key)).isFalse());

		// 詳細とタイムラインにも、見るための URL 付きで出る
		long postId = ((Number) JsonPath.read(body, "$.id")).longValue();
		String detail = as(me, get("/api/posts/" + postId), null)
				.andExpect(jsonPath("$.media.length()").value(4))
				.andReturn().getResponse().getContentAsString();
		HttpResponse<byte[]> view = http.send(
				HttpRequest.newBuilder(URI.create(JsonPath.read(detail, "$.media[0].url"))).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(view.statusCode()).isEqualTo(200);
		as(me, get("/api/posts?tab=following"), null)
				.andExpect(jsonPath("$.posts[0].media.length()").value(4));
	}

	@Test
	void 画像5枚は断る() throws Exception {
		long me = newUser();
		List<String> keys = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			keys.add(uploadedKey(me, jpeg(10, 10)));
		}
		expectMediaError(as(me, post("/api/posts"), json("5枚", keys)), "画像は4枚までです");
	}

	@Test
	void 他人の一時置き場の画像や_ないキーや_でたらめなキーは使えない() throws Exception {
		long me = newUser();
		long other = newUser();
		String othersKey = uploadedKey(other, jpeg(10, 10));
		expectMediaError(as(me, post("/api/posts"), json("他人の", List.of(othersKey))),
				"画像・動画が見つかりません。選び直してください");
		String missing = "tmp/" + me + "/" + UUID.randomUUID() + ".webp";
		expectMediaError(as(me, post("/api/posts"), json("ない", List.of(missing))),
				"画像・動画が見つかりません。選び直してください");
		expectMediaError(as(me, post("/api/posts"), json("でたらめ", List.of("../../etc/passwd"))),
				"画像・動画が見つかりません。選び直してください");
		// でたらめなキーは、組み合わせ（動画は1本まで）より先に断る
		expectMediaError(as(me, post("/api/posts"), json("でたらめ2本", List.of("a.mp4", "b.mp4"))),
				"画像・動画が見つかりません。選び直してください");
		// 断られた投稿は保存されていない
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts WHERE user_id = ?", Long.class, me))
				.isZero();
	}

	// ===== 動画 =====

	@Test
	void 動画は期限付きURLで直接送り_H264の2秒のMP4は投稿できる() throws Exception {
		long me = newUser();
		String key = uploadVideo(me, "h264-2s.mp4");

		String body = as(me, post("/api/posts"), json("動画", List.of(key)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.media.length()").value(1))
				.andExpect(jsonPath("$.media[0].type").value("video"))
				.andReturn().getResponse().getContentAsString();
		String postKey = JsonPath.read(body, "$.media[0].key");
		assertThat(postKey).matches("posts/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.mp4");
		assertThat(s3Object(postKey)).isEqualTo(resource("h264-2s.mp4"));
		assertThat(exists(key)).isFalse();
	}

	@Test
	void HEVCのMP4_MOV_141秒の動画は断り_S3からも消す() throws Exception {
		long me = newUser();
		String hevc = uploadVideo(me, "hevc-2s.mp4");
		expectMediaError(as(me, post("/api/posts"), json("HEVC", List.of(hevc))), "MP4 の動画を選んでください");
		assertThat(exists(hevc)).isFalse();

		String mov = uploadVideo(me, "h264-2s.mov");
		expectMediaError(as(me, post("/api/posts"), json("MOV", List.of(mov))), "MP4 の動画を選んでください");

		String longVideo = uploadVideo(me, "h264-141s.mp4");
		expectMediaError(as(me, post("/api/posts"), json("長い", List.of(longVideo))), "動画は100MB・2分20秒までです");
		assertThat(exists(longVideo)).isFalse();
	}

	@Test
	void 送り先の申し込みで100MBを超える動画は断る() throws Exception {
		long me = newUser();
		as(me, post("/api/media/videos"), "{\"size\": " + (100L * 1024 * 1024 + 1) + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("size"))
				.andExpect(jsonPath("$.errors[0].message").value("動画は100MB・2分20秒までです"));
	}

	@Test
	void 画像と動画は混ぜられず_動画は1本まで() throws Exception {
		long me = newUser();
		String image = uploadedKey(me, jpeg(10, 10));
		String video1 = uploadVideo(me, "h264-2s.mp4");
		String video2 = uploadVideo(me, "h264-2s.mp4");
		expectMediaError(as(me, post("/api/posts"), json("混ぜる", List.of(image, video1))),
				"画像と動画は一緒に投稿できません");
		expectMediaError(as(me, post("/api/posts"), json("2本", List.of(video1, video2))), "動画は1本までです");
	}

	// ===== 編集・削除 =====

	@Test
	void 編集で画像を外すとS3からも消え_足すこともでき_並び順は送った順になる() throws Exception {
		long me = newUser();
		String a = uploadedKey(me, jpeg(101, 10));
		String b = uploadedKey(me, jpeg(102, 10));
		String created = as(me, post("/api/posts"), json("2枚", List.of(a, b)))
				.andReturn().getResponse().getContentAsString();
		long postId = ((Number) JsonPath.read(created, "$.id")).longValue();
		List<String> keys = JsonPath.read(created, "$.media[*].key");

		// 1枚目を外し、新しい画像を前に足す
		String c = uploadedKey(me, jpeg(103, 10));
		String updated = as(me, put("/api/posts/" + postId), json("編集", List.of(c, keys.get(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.media.length()").value(2))
				.andExpect(jsonPath("$.media[1].key").value(keys.get(1)))
				.andReturn().getResponse().getContentAsString();
		assertThat(webpImage(JsonPath.read(updated, "$.media[0].key")).getWidth()).isEqualTo(103);
		assertThat(exists(keys.get(0))).isFalse();
		assertThat(exists(keys.get(1))).isTrue();

		// media を送らなければ、画像はそのまま
		as(me, put("/api/posts/" + postId), "{\"content\": \"本文だけ\"}")
				.andExpect(jsonPath("$.media.length()").value(2));

		// 空にすると、すべて外れる
		as(me, put("/api/posts/" + postId), json("なし", List.of()))
				.andExpect(jsonPath("$.media.length()").value(0));
		assertThat(exists(keys.get(1))).isFalse();
	}

	@Test
	void 他人の投稿の画像は_自分の投稿に付けられない() throws Exception {
		long me = newUser();
		long other = newUser();
		String created = as(other, post("/api/posts"), json("他人の画像", List.of(uploadedKey(other, jpeg(10, 10)))))
				.andReturn().getResponse().getContentAsString();
		String othersPostKey = JsonPath.read(created, "$.media[0].key");
		long myPost = ((Number) JsonPath.read(as(me, post("/api/posts"), json("自分", null))
				.andReturn().getResponse().getContentAsString(), "$.id")).longValue();

		expectMediaError(as(me, put("/api/posts/" + myPost), json("盗む", List.of(othersPostKey))),
				"画像・動画が見つかりません。選び直してください");
		assertThat(exists(othersPostKey)).isTrue();
	}

	@Test
	void 投稿を消すと画像もS3から消え_他人の投稿は消せない() throws Exception {
		long me = newUser();
		long other = newUser();
		String created = as(me, post("/api/posts"), json("消す", List.of(uploadedKey(me, jpeg(10, 10)))))
				.andReturn().getResponse().getContentAsString();
		long postId = ((Number) JsonPath.read(created, "$.id")).longValue();
		String key = JsonPath.read(created, "$.media[0].key");

		as(other, delete("/api/posts/" + postId), null).andExpect(status().isNotFound());
		assertThat(exists(key)).isTrue();

		as(me, delete("/api/posts/" + postId), null).andExpect(status().isNoContent());
		assertThat(exists(key)).isFalse();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM post_media WHERE s3_key = ?", Long.class, key))
				.isZero();
	}

	// ===== 道具 =====

	private long newUser() {
		String username = "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, "画像 太郎");
		assertThat(id).isNotNull();
		return id;
	}

	private ResultActions uploadImage(long userId, byte[] data, String filename, String contentType)
			throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", filename, contentType, data);
		return as(userId, multipart("/api/media/images").file(file), null);
	}

	private String uploadedKey(long userId, byte[] jpeg) throws Exception {
		return uploadedKey(userId, jpeg, "a.jpg", "image/jpeg");
	}

	private String uploadedKey(long userId, byte[] data, String filename, String contentType) throws Exception {
		String body = uploadImage(userId, data, filename, contentType)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.key");
	}

	/** 画面と同じ流れで動画を送る：送り先 URL をもらい、S3（S3Mock）へ直接 PUT する */
	private String uploadVideo(long userId, String resource) throws Exception {
		byte[] data = resource(resource);
		String body = as(userId, post("/api/media/videos"), "{\"size\": " + data.length + "}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		assertThat(key).matches("tmp/" + userId + "/[0-9a-f-]{36}\\.mp4");
		HttpResponse<Void> uploaded = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
				.header("Content-Type", "video/mp4")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(), HttpResponse.BodyHandlers.discarding());
		assertThat(uploaded.statusCode()).isEqualTo(200);
		return key;
	}

	private static void expectFileError(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("file"))
				.andExpect(jsonPath("$.errors[0].message").value(message));
	}

	private static void expectMediaError(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("media"))
				.andExpect(jsonPath("$.errors[0].message").value(message));
	}

	private static String json(String content, List<String> media) {
		String json = "{\"content\": \"" + JSONValue.escape(content) + "\"";
		if (media != null) {
			JSONArray array = new JSONArray();
			array.addAll(media);
			json += ", \"media\": " + array.toJSONString();
		}
		return json + "}";
	}

	private ResultActions as(long userId, AbstractMockHttpServletRequestBuilder<?> builder, String json)
			throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		builder.cookie(csrf, new Cookie(AuthCookies.ACCESS_TOKEN, jwtTokenProvider.createAccessToken(userId)))
				.header("X-XSRF-TOKEN", csrf.getValue());
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder);
	}

	private byte[] s3Object(String key) {
		return s3.getObjectAsBytes(b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key)).asByteArray();
	}

	private boolean exists(String key) {
		try {
			s3.headObject(b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key));
			return true;
		} catch (NoSuchKeyException e) {
			return false;
		} catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
			if (e.statusCode() == 404) {
				return false;
			}
			throw e;
		}
	}

	private BufferedImage webpImage(String key) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(s3Object(key)));
	}

	private static byte[] resource(String name) throws IOException {
		try (InputStream in = PostMediaApiTest.class.getResourceAsStream("/media/" + name)) {
			assertThat(in).as(name).isNotNull();
			return in.readAllBytes();
		}
	}

	/** 左上が赤、ほかが青の絵（回したかどうかを見分けやすい） */
	private static BufferedImage picture(int width, int height, int type) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.RED);
		g.fillRect(0, 0, Math.max(1, width / 4), Math.max(1, height / 4));
		g.dispose();
		return image;
	}

	private static BufferedImage filled(int width, int height, Color color) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, width, height);
		g.dispose();
		return image;
	}

	private static byte[] jpeg(int width, int height) throws IOException {
		return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, out)).as(format).isTrue();
		return out.toByteArray();
	}

	/**
	 * JPEG の先頭（SOI の直後）に Exif（APP1）を入れる。中身は Orientation=6 と、位置情報（GPSLatitudeRef=N）。
	 * Exif の中は TIFF の形：ヘッダー → IFD0（Orientation と GPS の場所）→ GPS の IFD
	 */
	private static byte[] withExif(byte[] jpeg) {
		ByteBuffer tiff = ByteBuffer.allocate(8 + 30 + 18);
		tiff.put(new byte[] {'M', 'M', 0, 42}).putInt(8);
		// IFD0：2つの項目
		tiff.putShort((short) 2);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0);
		tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(8 + 30);
		tiff.putInt(0);
		// GPS の IFD：1つの項目（北緯）
		tiff.putShort((short) 1);
		tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put(new byte[] {'N', 0, 0, 0});
		tiff.putInt(0);

		byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
		int length = 2 + exifHeader.length + tiff.capacity();
		ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + length);
		out.put(jpeg, 0, 2);
		out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(exifHeader).put(tiff.array());
		out.put(jpeg, 2, jpeg.length - 2);
		return out.array();
	}

	private static boolean contains(byte[] data, String text) {
		byte[] target = text.getBytes(StandardCharsets.US_ASCII);
		outer: for (int i = 0; i + target.length <= data.length; i++) {
			for (int j = 0; j < target.length; j++) {
				if (data[i + j] != target[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

}
