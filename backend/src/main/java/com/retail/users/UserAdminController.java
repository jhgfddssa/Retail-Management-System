package com.retail.users;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users") @PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserAdminController {
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;public UserAdminController(JdbcTemplate j,PasswordEncoder e){jdbc=j;encoder=e;}
 public record CreateUser(@NotBlank @Size(max=120) String username,@NotBlank @Size(min=12,max=100) String password,@NotBlank String displayName,@NotEmpty List<String> roles,List<UUID> branchIds){ @Override public String toString(){return "CreateUser[username="+username+", password=[REDACTED], displayName="+displayName+", roles="+roles+"]";} }
 @PostMapping @Transactional public Map<String,Object> create(@Valid @RequestBody CreateUser r){UUID id=UUID.randomUUID();jdbc.update("insert into users(id,username,password_hash,display_name) values (?,?,?,?)",id,r.username().trim(),encoder.encode(r.password()),r.displayName().trim());for(String role:r.roles()){int n=jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where code=?",id,role);if(n!=1)throw new IllegalArgumentException("Unknown role: "+role);}if(r.branchIds()!=null)for(UUID branch:r.branchIds())jdbc.update("insert into user_branches(user_id,branch_id) values (?,?)",id,branch);return Map.of("id",id,"username",r.username(),"displayName",r.displayName());}
 @PutMapping("/{id}/branches") @Transactional public Map<String,Object> branches(@PathVariable UUID id,@RequestBody List<UUID> ids){jdbc.update("delete from user_branches where user_id=?",id);for(UUID branch:ids)jdbc.update("insert into user_branches(user_id,branch_id) values (?,?)",id,branch);return Map.of("userId",id,"branchIds",ids);}
 @PutMapping("/{id}/roles") @Transactional public Map<String,Object> roles(@PathVariable UUID id,@RequestBody List<String> codes){jdbc.update("delete from user_roles where user_id=?",id);for(String code:codes){if(jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where code=?",id,code)!=1)throw new IllegalArgumentException("Unknown role: "+code);}return Map.of("userId",id,"roles",codes);}
 @PatchMapping("/{id}/status") public Map<String,Object> status(@PathVariable UUID id,@RequestBody Map<String,Boolean> body){Boolean active=body.get("active");if(active==null)throw new IllegalArgumentException("active is required");jdbc.update("update users set active=? where id=?",active,id);if(!active)jdbc.update("update auth_sessions set revoked_at=now() where user_id=? and revoked_at is null",id);return Map.of("id",id,"active",active);}
}
