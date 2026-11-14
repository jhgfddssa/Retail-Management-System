package com.retail.auth;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.bind.annotation.*;
import com.retail.common.security.TokenAuthenticationFilter;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthenticationManager manager;private final JdbcTemplate jdbc;private final SecureRandom random=new SecureRandom();public AuthController(AuthenticationConfiguration c,JdbcTemplate j)throws Exception{manager=c.getAuthenticationManager();jdbc=j;}
 public record Login(@NotBlank String username,@NotBlank String password){ @Override public String toString(){return "Login[username="+username+", password=[REDACTED]]";} }
 public record LoginResponse(String token,String username,List<String> roles,int expiresIn){ @Override public String toString(){return "LoginResponse[token=[REDACTED], username="+username+", roles="+roles+", expiresIn="+expiresIn+"]";} }
 @PostMapping("/login") public LoginResponse login(@Valid @RequestBody Login body){Authentication a=manager.authenticate(new UsernamePasswordAuthenticationToken(body.username(),body.password()));byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);UUID id=jdbc.queryForObject("select id from users where username=?",UUID.class,a.getName());jdbc.update("insert into auth_sessions(token_hash,user_id,expires_at) values (?,?,?)",TokenAuthenticationFilter.hash(token),id,java.sql.Timestamp.from(Instant.now().plusSeconds(28800)));return new LoginResponse(token,a.getName(),a.getAuthorities().stream().filter(x->x.getAuthority().startsWith("ROLE_")).map(x->x.getAuthority().replace("ROLE_","" )).toList(),28800);}
 @GetMapping("/me") public Map<String,Object> me(Authentication a){UUID id=jdbc.queryForObject("select id from users where username=?",UUID.class,a.getName());return Map.of("id",id,"username",a.getName(),"roles",a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","")).toList(),"branches",jdbc.queryForList("select b.id,b.code,b.name from branches b join user_branches ub on ub.branch_id=b.id where ub.user_id=?",id));}
 @PostMapping("/logout") public Map<String,String> logout(@RequestHeader(value="Authorization",required=false) String h){if(h!=null&&h.startsWith("Bearer "))jdbc.update("update auth_sessions set revoked_at=now() where token_hash=?",TokenAuthenticationFilter.hash(h.substring(7)));return Map.of("status","logged_out");}
}
