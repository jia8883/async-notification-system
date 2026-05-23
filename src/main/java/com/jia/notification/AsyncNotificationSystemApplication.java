package com.jia.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AsyncNotificationSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsyncNotificationSystemApplication.class, args);

	}

}
