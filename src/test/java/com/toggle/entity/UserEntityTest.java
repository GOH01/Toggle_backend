package com.toggle.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UserEntityTest {

    @Test
    void userShouldNotDeclareMapBridgeFields() {
        Stream.of("defaultMapId", "publicMapUuid", "mapTitle", "mapDescription", "publicMap")
            .forEach(fieldName ->
                assertThatThrownBy(() -> User.class.getDeclaredField(fieldName))
                    .isInstanceOf(NoSuchFieldException.class)
            );
    }

    @Test
    void userShouldStillKeepIdentityAndProfileFields() throws Exception {
        Field emailField = User.class.getDeclaredField("email");
        Field passwordField = User.class.getDeclaredField("password");
        Field nicknameField = User.class.getDeclaredField("nickname");
        Field profileImageField = User.class.getDeclaredField("profileImageUrl");

        assertThat(emailField).isNotNull();
        assertThat(passwordField).isNotNull();
        assertThat(nicknameField).isNotNull();
        assertThat(profileImageField).isNotNull();
    }
}
