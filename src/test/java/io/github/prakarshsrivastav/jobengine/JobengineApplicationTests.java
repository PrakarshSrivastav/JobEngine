package io.github.prakarshsrivastav.jobengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, TestJobHandlers.class})
@SpringBootTest
@ActiveProfiles("test")
class JobengineApplicationTests {

	@Test
	void contextLoads() {
	}

}
