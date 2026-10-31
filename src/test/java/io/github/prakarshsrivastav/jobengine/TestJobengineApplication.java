package io.github.prakarshsrivastav.jobengine;

import org.springframework.boot.SpringApplication;

public class TestJobengineApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobengineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
