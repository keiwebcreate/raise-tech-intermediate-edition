package io.github.keiwebcreate.raisetechtimeline.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;

/**
 * API 仕様書（Swagger）のテスト。テストでは application-test.yml で仕様書を出している。
 * 作った仕様書は build/openapi.json にも書き出す（中身を目で確かめるため）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SwaggerApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 仕様書はログインしなくても読め_すべてのAPIに日本語の説明とまとまりがある() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("RaiseTechtimeline API"))
				.andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("ACCESS_TOKEN"))
				.andExpect(jsonPath("$.components.schemas.Problem").exists())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Files.writeString(Path.of("build", "openapi.json"), json, StandardCharsets.UTF_8);

		Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(json, "$.paths");
		assertThat(paths).isNotEmpty();
		paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
			String name = method.toUpperCase() + " " + path;
			assertThat(operation.get("summary")).as("%s の説明", name).isNotNull();
			assertThat((List<?>) operation.get("tags")).as("%s のまとまり", name).hasSize(1);
		}));
	}

	@Test
	void ログインが必要なAPIには401が_GET以外には403が載る() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/posts'].post.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/posts'].post.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/api/posts'].get.responses['403']").doesNotExist())
				// ログインは、ログインしていなくても呼べる
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401'].description")
						.value(not(containsString("UNAUTHENTICATED"))));
	}

	@Test
	void 仕様書の画面は_読み込みを許すCSPで開け_CSRFのCookieが付く() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				// 開いた時点で Cookie が付くので、画面から最初の POST（ログインなど）をそのまま試せる
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(header().string("Content-Security-Policy",
						containsString("default-src 'self'")));
	}

	@Test
	void APIの返事は今までどおり何も読み込ませないCSP() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(header().string("Content-Security-Policy",
						containsString("default-src 'none'")));
	}

}
