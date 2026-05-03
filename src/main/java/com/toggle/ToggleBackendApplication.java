package com.toggle;

import com.toggle.global.config.JwtProperties;
import com.toggle.global.config.S3Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, S3Properties.class})
public class ToggleBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToggleBackendApplication.class, args);
    }
}
