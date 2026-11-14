package com.retail.common.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
class BranchAccessTest {
 @Test void allowsOnlyAssignedBranch(){JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID assigned=UUID.randomUUID(),other=UUID.randomUUID();when(jdbc.queryForList(anyString(),eq(UUID.class),eq("manager"))).thenReturn(List.of(assigned));var access=new BranchAccess(jdbc);var user=new UsernamePasswordAuthenticationToken("manager","",List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));assertDoesNotThrow(()->access.requireBranch(user,assigned));assertThrows(AccessDeniedException.class,()->access.requireBranch(user,other));}
 @Test void superAdminHasGlobalBranchScope(){JdbcTemplate jdbc=mock(JdbcTemplate.class);var access=new BranchAccess(jdbc);var admin=new UsernamePasswordAuthenticationToken("root","",List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));assertDoesNotThrow(()->access.requireBranch(admin,UUID.randomUUID()));verifyNoInteractions(jdbc);}
}
