package com.retail.common.security;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component
public class BranchAccess {
 private final JdbcTemplate jdbc; public BranchAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean global(Authentication a){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_SUPER_ADMIN"));}
 public List<UUID> branches(Authentication a){if(global(a))return jdbc.queryForList("select id from branches",UUID.class);return jdbc.queryForList("select ub.branch_id from user_branches ub join users u on u.id=ub.user_id where u.username=?",UUID.class,a.getName());}
 public void requireBranch(Authentication a,UUID branch){if(!global(a)&&!branches(a).contains(branch))throw new org.springframework.security.access.AccessDeniedException("Branch access denied");}
}
