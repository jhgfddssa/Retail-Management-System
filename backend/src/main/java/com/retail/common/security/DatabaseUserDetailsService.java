package com.retail.common.security;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service
public class DatabaseUserDetailsService implements UserDetailsService {
 private final JdbcTemplate jdbc; public DatabaseUserDetailsService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public UserDetails loadUserByUsername(String username)throws UsernameNotFoundException{var users=jdbc.queryForList("select id,username,password_hash,active from users where lower(username)=lower(?)",username);if(users.isEmpty())throw new UsernameNotFoundException("Invalid username or password");var u=users.getFirst();var roles=jdbc.queryForList("select r.code from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",String.class,u.get("id"));return User.withUsername((String)u.get("username")).password((String)u.get("password_hash")).disabled(!Boolean.TRUE.equals(u.get("active"))).authorities(roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList()).build();}
}
