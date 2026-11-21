package io.github.keiwebcreate.raisetechtimeline.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;

/**
 * 本番と同じ設定（仕様書を出さない）のとき、仕様書の住所が開けないこと。
 * application.yml の初期値（false）に戻して確かめる（テストの設定 application-test.yml では true にしている）
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SwaggerDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 本番の設定では_仕様書も画面も開けない() throws Exception {
		// 仕様書の住所に特別な決まりがないので、ほかの API と同じく、ログインしていなければ 401
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
	}

}
