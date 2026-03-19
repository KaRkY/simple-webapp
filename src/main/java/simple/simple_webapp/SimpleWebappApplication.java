package simple.simple_webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
public class SimpleWebappApplication {

	public static void main(String[] args) {
			SpringApplication.run(SimpleWebappApplication.class, args);
	}

}
