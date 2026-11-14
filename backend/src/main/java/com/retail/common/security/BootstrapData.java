package com.retail.common.security;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class BootstrapData {
 @Bean ApplicationRunner bootstrap(JdbcTemplate jdbc, PasswordEncoder encoder, Environment environment) { return args -> {
   if (jdbc.queryForObject("select count(*) from users", Integer.class)==0) {
     String username=environment.getProperty("ADMIN_USERNAME","admin");
     String password=environment.getProperty("ADMIN_PASSWORD");
     if(password==null||password.length()<12) throw new IllegalStateException("Set ADMIN_PASSWORD to a unique value of at least 12 characters before first startup");
     jdbc.update("insert into users(username,password_hash,display_name) values (?,?,?)",username,encoder.encode(password),"System Administrator");
     jdbc.update("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r where u.username=? and r.code='SUPER_ADMIN'",username);
   }
   jdbc.update("insert into locations(code,name,type) values ('CENTRAL','Central Warehouse','CENTRAL_WAREHOUSE') on conflict (code) do nothing");
   jdbc.update("insert into locations(code,name,type) values ('IN-TRANSIT','In Transit','IN_TRANSIT') on conflict (code) do nothing");
 }; }
}



