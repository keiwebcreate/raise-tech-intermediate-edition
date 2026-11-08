package io.github.keiwebcreate.raisetechtimeline;

import org.springframework.boot.SpringApplication;

public class TestRaiseTechtimelineApplication {

	public static void main(String[] args) {
		SpringApplication.from(RaiseTechtimelineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
