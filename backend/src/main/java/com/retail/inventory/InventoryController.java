package com.retail.inventory;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.retail.common.security.BranchAccess;
import com.retail.common.audit.AuditService;
@RestController @RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final JdbcTemplate jdbc; private final BranchAccess access; private final AuditService audit;
 public InventoryController(JdbcTemplate jdbc,BranchAccess access,AuditService audit){this.jdbc=jdbc;this.access=access;this.audit=audit;}
 public record Adjustment(@NotNull UUID locationId,@NotNull UUID productId,@NotNull Integer quantityDelta,@NotBlank String reason){}
 @PostMapping("/adjustments") @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_MANAGER','INVENTORY_STAFF')")
 public Map<String,Object> adjust(@Valid @RequestBody Adjustment r,Authentication auth){
  var loc=jdbc.queryForMap("select id,branch_id,type from locations where id=? and active=true",r.locationId());
  if(loc.get("branch_id")!=null)access.requireBranch(auth,(UUID)loc.get("branch_id"));
  if(r.quantityDelta()==0)throw new IllegalArgumentException("Adjustment cannot be zero");
  if(!access.global(auth)&&"CENTRAL_WAREHOUSE".equals(loc.get("type")))throw new org.springframework.security.access.AccessDeniedException("Central warehouse adjustment requires administrator");
  var id=UUID.randomUUID();int changed;
  if(r.quantityDelta()>0){
   changed=jdbc.update("insert into stock_balances(location_id,product_id,quantity) values (?,?,?) on conflict(location_id,product_id) do update set quantity=stock_balances.quantity+excluded.quantity,version=stock_balances.version+1,updated_at=now()",r.locationId(),r.productId(),r.quantityDelta());
  }else{
   long requiredQuantity=-(long)r.quantityDelta();
   changed=jdbc.update("update stock_balances set quantity=quantity+?,version=version+1,updated_at=now() where location_id=? and product_id=? and quantity>=?",r.quantityDelta(),r.locationId(),r.productId(),requiredQuantity);
  }
  if(changed!=1)throw new IllegalStateException("Insufficient stock");
  Integer quantity=jdbc.queryForObject("select quantity from stock_balances where location_id=? and product_id=?",Integer.class,r.locationId(),r.productId());
  if(quantity==null||quantity<0)throw new IllegalStateException("Insufficient stock");
  UUID actor=jdbc.queryForObject("select id from users where username=?",UUID.class,auth.getName());
  jdbc.update("insert into stock_movements(location_id,product_id,quantity_delta,movement_type,source_type,source_id,actor_id) values (?,?,?,'ADJUSTMENT',?,?,?)",r.locationId(),r.productId(),r.quantityDelta(),r.reason(),id,actor);
  audit.record(auth.getName(),"STOCK_ADJUSTED","STOCK_BALANCE",id,(UUID)loc.get("branch_id"));
  return Map.of("id",id,"quantity",quantity);
 }
}



