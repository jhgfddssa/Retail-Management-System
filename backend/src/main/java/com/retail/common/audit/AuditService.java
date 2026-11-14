package com.retail.common.audit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class AuditService {
 private final JdbcTemplate jdbc;public AuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(String username,String action,String type,UUID entityId,UUID branchId){UUID actor=username==null?null:jdbc.queryForObject("select id from users where username=?",UUID.class,username);jdbc.update("insert into audit_events(actor_id,action,entity_type,entity_id,branch_id) values (?,?,?,?,?)",actor,action,type,entityId,branchId);}
}
