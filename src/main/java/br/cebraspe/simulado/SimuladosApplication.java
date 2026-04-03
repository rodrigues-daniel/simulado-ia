package br.cebraspe.simulado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimuladosApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimuladosApplication.class, args);
	}

}
