package com.example.duolingomathbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DuolingomathbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(DuolingomathbotApplication.class, args);
		System.out.println("DuolingoMathBotApplication started!");
		System.out.println("Make sure your PostgreSQL is running and configured in application.properties.");
		System.out.println("Bot token and username should also be set in application.properties.");
	}
}