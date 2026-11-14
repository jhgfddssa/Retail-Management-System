package com.retail.sales;
import java.math.BigDecimal;
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
@RestController @RequestMapping("/api/v1/sales")
public class SaleController {
 private final JdbcTemplate jdbc; private final BranchAccess access; private final AuditService audit;
 public SaleController(JdbcTemplate jdbc,BranchAccess access,AuditService audit){this.jdbc=jdbc;this.access=access;this.audit=audit;}
 public record Line(@NotNull UUID productId,@Min(1) int quantity,@NotNull @DecimalMin("0") BigDecimal discount){}
 public record Payment(@NotNull @DecimalMin(value="0.01") BigDecimal amount,@NotBlank String method){}
 public record SaleRequest(@NotNull UUID branchId,UUID customerId,@NotBlank @Pattern(regexp="USD|KHR") String currency,@NotEmpty @Valid List<Line> lines,@NotEmpty @Valid List<Payment> payments){}
 @PostMapping @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_MANAGER','CASHIER')")
 public Map<String,Object> create(@Valid @RequestBody SaleRequest req,@RequestHeader("Idempotency-Key") String key,Authentication auth){
  access.requireBranch(auth,req.branchId()); if(key.length()>100)throw new IllegalArgumentException("Idempotency key too long");var prior=jdbc.queryForList("select id,invoice_no,total,currency_code from sales where idempotency_key=?",key);if(!prior.isEmpty())return prior.getFirst();
  if(!req.currency().equals("USD")&&!req.currency().equals("KHR"))throw new IllegalArgumentException("Unsupported currency");
  if(req.customerId()!=null){var customer=jdbc.queryForMap("select branch_id from customers where id=?",req.customerId());if(customer.get("branch_id")!=null&&!req.branchId().equals(customer.get("branch_id")))throw new org.springframework.security.access.AccessDeniedException("Customer is assigned to another branch");}UUID cashier=jdbc.queryForObject("select id from users where username=?",UUID.class,auth.getName());BigDecimal subtotal=BigDecimal.ZERO,discountTotal=BigDecimal.ZERO;var priced=new ArrayList<Map<String,Object>>();
  for(Line line:req.lines()){var row=jdbc.queryForMap("select p.sku,p.name,u.name unit_name,pp.amount from products p join units u on u.id=p.unit_id join product_prices pp on pp.product_id=p.id and pp.currency_code=? and pp.effective_to is null where p.id=? and p.active=true",req.currency(),line.productId());BigDecimal unit=(BigDecimal)row.get("amount"),gross=unit.multiply(BigDecimal.valueOf(line.quantity())),disc=line.discount();BigDecimal cap=auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_SUPER_ADMIN"))?BigDecimal.ONE:BigDecimal.valueOf(auth.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_CASHIER"))?0.10:0.50);BigDecimal maxDiscount=gross.multiply(cap);if(disc.compareTo(maxDiscount)>0)throw new IllegalArgumentException("Discount exceeds your role limit");var amount=gross.subtract(disc);subtotal=subtotal.add(gross);discountTotal=discountTotal.add(disc);priced.add(Map.of("line",line,"row",row,"unit",unit,"amount",amount));}
  BigDecimal total=subtotal.subtract(discountTotal),paid=req.payments().stream().map(Payment::amount).reduce(BigDecimal.ZERO,BigDecimal::add);if(paid.compareTo(total)!=0)throw new IllegalArgumentException("Payment total must equal invoice total");
  String invoice="S-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);UUID sale=UUID.randomUUID();jdbc.update("insert into sales(id,branch_id,invoice_no,cashier_id,customer_id,status,currency_code,subtotal,discount_total,total,idempotency_key) values (?,?,?,?,?,'COMPLETED',?,?,?,?,?)",sale,req.branchId(),invoice,cashier,req.customerId(),req.currency(),subtotal,discountTotal,total,key);
  for(var item:priced){Line line=(Line)item.get("line");var row=(Map<String,Object>)item.get("row");UUID movementId=UUID.randomUUID();int changed=jdbc.update("update stock_balances set quantity=quantity-?,version=version+1,updated_at=now() where location_id=(select id from locations where branch_id=? and type='BRANCH') and product_id=? and quantity>=?",line.quantity(),req.branchId(),line.productId(),line.quantity());if(changed!=1)throw new IllegalStateException("Insufficient branch stock");UUID lineId=UUID.randomUUID();jdbc.update("insert into sale_lines(id,sale_id,product_id,sku_snapshot,name_snapshot,unit_snapshot,quantity,unit_price,discount,line_total) values (?,?,?,?,?,?,?,?,?,?)",lineId,sale,line.productId(),row.get("sku"),row.get("name"),row.get("unit_name"),line.quantity(),item.get("unit"),line.discount(),item.get("amount"));jdbc.update("insert into stock_movements(id,location_id,product_id,quantity_delta,movement_type,source_type,source_id,source_line_id,actor_id) values (?,(select id from locations where branch_id=? and type='BRANCH'),?,?,'SALE','SALE',?,?,?)",movementId,req.branchId(),line.productId(),-line.quantity(),sale,lineId,cashier);}
  for(Payment p:req.payments())jdbc.update("insert into sale_payments(sale_id,currency_code,amount,method) values (?,?,?,?)",sale,req.currency(),p.amount(),p.method());
  audit.record(auth.getName(),"SALE_COMPLETED","SALE",sale,req.branchId());return Map.of("id",sale,"invoiceNo",invoice,"currency",req.currency(),"subtotal",subtotal,"discount",discountTotal,"total",total,"status","COMPLETED");
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id,Authentication auth){var sale=jdbc.queryForMap("select * from sales where id=?",id);access.requireBranch(auth,(UUID)sale.get("branch_id"));return Map.of("sale",sale,"lines",jdbc.queryForList("select * from sale_lines where sale_id=?",id),"payments",jdbc.queryForList("select * from sale_payments where sale_id=?",id));}
}






