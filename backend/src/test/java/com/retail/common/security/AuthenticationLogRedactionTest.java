package com.retail.common.security;

import com.retail.auth.AuthController;
import com.retail.users.UserAdminController;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationLogRedactionTest {
    @Test
    void loginRequestAndResponseNeverRenderCredentials() {
        var password = "unit-test-password-value";
        var token = "unit-test-bearer-token";
        var request = new AuthController.Login("test-user", password).toString();
        var response = new AuthController.LoginResponse(token, "test-user", List.of("SUPER_ADMIN"), 28800).toString();
        assertFalse(request.contains(password));
        assertTrue(request.contains("[REDACTED]"));
        assertFalse(response.contains(token));
        assertTrue(response.contains("[REDACTED]"));
    }

    @Test
    void userCreationRequestNeverRendersPassword() {
        var password = "unit-test-user-password";
        var request = new UserAdminController.CreateUser("test-user", password, "Test User", List.of("CASHIER"), List.of()).toString();
        assertFalse(request.contains(password));
        assertTrue(request.contains("[REDACTED]"));
    }
}