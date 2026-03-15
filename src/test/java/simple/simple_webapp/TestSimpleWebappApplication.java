package simple.simple_webapp;

import org.springframework.boot.SpringApplication;

public class TestSimpleWebappApplication {

	public static void main(String[] args) {
		SpringApplication
				.from(SimpleWebappApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}

}
