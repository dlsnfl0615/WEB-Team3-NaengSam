package com.naengsam.quick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuickApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuickApplication.class, args);

		if (true) System.out.println("hello");

		if (true)
			System.out.println("hello");

		if (true) {
			System.out.println("hello");
		}

		for (int i = 0; i < 1; i++) {
			System.out.println("i = " + i);
		}
	}
}
