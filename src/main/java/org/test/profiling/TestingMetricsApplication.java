package org.test.profiling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class TestingMetricsApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestingMetricsApplication.class, args);
	}

}
