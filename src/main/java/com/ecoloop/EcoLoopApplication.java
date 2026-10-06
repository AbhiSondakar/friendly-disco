package com.ecoloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class EcoLoopApplication {
  public static void main(String[] args) {
    SpringApplication.run(EcoLoopApplication.class, args);
  }
}