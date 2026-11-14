package com.retail.common.security;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
 private final JdbcTemplate jdbc;private final UserDetailsService users;public TokenAuthenticationFilter(JdbcTemplate j,UserDetailsService u){jdbc=j;users=u;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){try{String hash=hash(h.substring(7));var found=jdbc.queryForList("select u.username from auth_sessions s join users u on u.id=s.user_id where s.token_hash=? and s.revoked_at is null and s.expires_at>now() and u.active=true",String.class,hash);if(!found.isEmpty()){var user=users.loadUserByUsername(found.getFirst());var auth=new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));SecurityContextHolder.getContext().setAuthentication(auth);}}catch(Exception ignored){SecurityContextHolder.clearContext();}}chain.doFilter(req,res);}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
