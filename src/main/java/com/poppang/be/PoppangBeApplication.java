package com.poppang.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class PoppangBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(PoppangBeApplication.class, args);
  }
}
