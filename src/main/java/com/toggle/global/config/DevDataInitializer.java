package com.toggle.global.config;

import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DevDataInitializer {

    @Bean
    @Profile("!test")
    CommandLineRunner seedDemoUser(UserRepository userRepository) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            userRepository.save(new User(
                "demo@toggle.com",
                "temporary-password",
                "toggle-demo",
                UserRole.USER,
                UserStatus.ACTIVE
            ));
        };
    }
}
