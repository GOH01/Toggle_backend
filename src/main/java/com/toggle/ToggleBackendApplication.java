package com.toggle;

import com.toggle.global.config.JwtProperties;
import com.toggle.global.config.OwnerDocumentStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, OwnerDocumentStorageProperties.class})
public class ToggleBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToggleBackendApplication.class, args);
    }
}
