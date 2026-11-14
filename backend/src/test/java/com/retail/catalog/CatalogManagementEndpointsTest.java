package com.retail.catalog;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.List;

import com.retail.branches.BranchRepository;
import com.retail.common.audit.AuditService;
import com.retail.common.security.BranchAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest(controllers = {DirectoryController.class, ProductController.class})
@Import(CatalogManagementEndpointsTest.MethodSecurityTestConfig.class)
class CatalogManagementEndpointsTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurityTestConfig {}
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean BranchAccess branchAccess;
    @MockitoBean BranchRepository branchRepository;
    @MockitoBean AuditService audit;
    @MockitoBean UserDetailsService userDetailsService;

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void updatesBranchAndItsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("from branches where id=?"), eq(id))).thenReturn(List.of(Map.of("id", id)));
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        mvc.perform(put("/api/v1/branches/{id}", id).with(authentication(adminPrincipal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NORTH\",\"name\":\"North Store\",\"address\":\"Main Road\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("NORTH"));
        verify(jdbc).update(contains("update branches set code"), eq("NORTH"), eq("North Store"), eq("Main Road"), eq(id));
        verify(jdbc).update(contains("update locations set code"), eq("NORTH"), eq("North Store"), eq(id));
        verify(audit).record("user", "BRANCH_UPDATED", "BRANCH", id, null);
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void refusesBranchDeactivationWithActiveUsers() throws Exception {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("select active from branches"), eq(id))).thenReturn(List.of(Map.of("active", true)));
        when(jdbc.queryForObject(contains("count(*) from user_branches"), eq(Integer.class), eq(id))).thenReturn(1);
        when(jdbc.queryForObject(contains("count(*) from stock_balances"), eq(Integer.class), eq(id))).thenReturn(0);
        when(jdbc.queryForObject(contains("count(*) from stock_transfers"), eq(Integer.class), eq(id))).thenReturn(0);
        mvc.perform(patch("/api/v1/branches/{id}/status", id).with(authentication(adminPrincipal())).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isConflict());
        verify(jdbc, never()).update(contains("update branches set active"), anyBoolean(), eq(id));
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void activatesOrDeactivatesBranchWhenNoBusinessConstraintsRemain() throws Exception {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForList(contains("select active from branches"), eq(id))).thenReturn(List.of(Map.of("active", true)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(id))).thenReturn(0);
        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
        when(jdbc.queryForObject(contains("count(*) from user_branches"), eq(Integer.class), eq(id))).thenReturn(0);
        when(jdbc.queryForObject(contains("count(*) from stock_balances"), eq(Integer.class), eq(id))).thenReturn(0);
        when(jdbc.queryForObject(contains("count(*) from stock_transfers"), eq(Integer.class), eq(id))).thenReturn(0);
        mvc.perform(patch("/api/v1/branches/{id}/status", id).with(authentication(adminPrincipal())).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(jdbc).update(contains("update locations set active"), eq(false), eq(id));
    }

    @Test @WithMockUser(roles = "BRANCH_MANAGER")
    void branchManagerCannotManageBranchesOrUnits() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(put("/api/v1/branches/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NORTH\",\"name\":\"North Store\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/units").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BOX\",\"name\":\"Box\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void createsUnitAndRejectsInvalidOrDuplicateValues() throws Exception {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        mvc.perform(post("/api/v1/units").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BOX\",\"name\":\"Box\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BOX"));
        mvc.perform(post("/api/v1/units").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"name\":\"Box\"}")).andExpect(status().isBadRequest());
        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        mvc.perform(post("/api/v1/units").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BOX\",\"name\":\"Box\"}")).andExpect(status().isConflict());
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void updatesProductAndKeepsPriceHistoryAndCanChangeStatus() throws Exception {
        UUID id = UUID.randomUUID(), unit = UUID.randomUUID();
        when(jdbc.queryForList(contains("select id from products"), eq(id))).thenReturn(List.of(Map.of("id", id)));
        when(jdbc.queryForList(contains("select currency_code,amount"), eq(id))).thenReturn(List.of(
            Map.of("currency_code", "USD", "amount", new BigDecimal("2.00")),
            Map.of("currency_code", "KHR", "amount", new BigDecimal("8000.00"))));
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
        when(jdbc.update(contains("set active=?"), eq(false), eq(id))).thenReturn(1);
        String product = productJson(unit, "3.00", "8000.00");
        mvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(product))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-1"));
        verify(jdbc).update(contains("set effective_to=now()"), eq(id));
        verify(jdbc).update(contains("insert into product_prices"), eq(id), eq(new BigDecimal("3.00")), eq(id), eq(new BigDecimal("8000.00")));
        mvc.perform(patch("/api/v1/products/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void rejectsInvalidAndDuplicateProductUpdates() throws Exception {
        UUID id = UUID.randomUUID(), unit = UUID.randomUUID();
        String invalid = productJson(unit, "1.12345", "100.00");
        mvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
        when(jdbc.queryForList(contains("select id from products"), eq(id))).thenReturn(List.of(Map.of("id", id)));
        when(jdbc.update(contains("update products set"), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("duplicate SKU"));
        mvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(productJson(unit, "1.00", "100.00"))).andExpect(status().isConflict());
    }

    @Test @WithMockUser(roles = "CASHIER")
    void cashierCannotUpdateOrDeactivateProducts() throws Exception {
        UUID id = UUID.randomUUID(), unit = UUID.randomUUID();
        mvc.perform(put("/api/v1/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(productJson(unit, "1.00", "100.00"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/products/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    private String productJson(UUID unit, String usd, String khr) {
        return "{\"sku\":\"SKU-1\",\"barcode\":\"B-1\",\"name\":\"Widget\",\"unitId\":\"%s\",\"reorderLevel\":0,\"usdPrice\":%s,\"khrPrice\":%s}".formatted(unit, usd, khr);
    }
    private UsernamePasswordAuthenticationToken adminPrincipal() {
        return new UsernamePasswordAuthenticationToken("user", "", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }
}
