package com.retail.common.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retail.common.api.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, TokenAuthenticationFilter.class, HealthEndpointSecurityTest.SecurityTestConfig.class})
class HealthEndpointSecurityTest {
    @EnableWebSecurity
    static class SecurityTestConfig {}

    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void healthIsPublicAndReportsUpWhenDatabaseResponds() throws Exception {
        when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);

        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("retail-management-api"));
    }

    @Test
    void otherApiEndpointsRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/products"))
            .andExpect(status().isUnauthorized());
    }
}
