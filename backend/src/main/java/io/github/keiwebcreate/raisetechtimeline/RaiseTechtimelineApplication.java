package io.github.keiwebcreate.raisetechtimeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @ConfigurationPropertiesScan：application.yml の app.* を読む record（AuthProperties など）を探して使えるようにする
// @EnableScheduling：決まった時刻に動く処理（期限切れのトークンの片付けなど）を動かす
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RaiseTechtimelineApplication {

	public static void main(String[] args) {
		SpringApplication.run(RaiseTechtimelineApplication.class, args);
	}

}
