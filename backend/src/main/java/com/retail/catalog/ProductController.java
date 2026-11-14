package com.retail.catalog;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/products")
public class ProductController {
 private final JdbcTemplate jdbc; public ProductController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record ProductRequest(@NotBlank @Size(max=64) String sku,@Size(max=80) String barcode,@NotBlank @Size(max=180) String name,String description,UUID categoryId,UUID brandId,@NotNull UUID unitId,@Min(0) int reorderLevel,@NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal usdPrice,@NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal khrPrice){}
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q){int s=Math.clamp(size,1,100),p=Math.max(0,page);String filter=q==null||q.isBlank()?"":" where lower(p.name) like ? or lower(p.sku) like ?";Object[] args=filter.isEmpty()?new Object[]{s,p*s}:new Object[]{"%"+q.toLowerCase()+"%","%"+q.toLowerCase()+"%",s,p*s};var rows=jdbc.queryForList("select p.id,p.sku,p.barcode,p.name,p.description,p.category_id,p.brand_id,p.unit_id,p.reorder_level,p.active,coalesce((select amount from product_prices where product_id=p.id and currency_code='USD' and effective_to is null),0) usd_price,coalesce((select amount from product_prices where product_id=p.id and currency_code='KHR' and effective_to is null),0) khr_price from products p"+filter+" order by name limit ? offset ?",args);return Map.of("items",rows,"page",p,"size",s,"totalItems",jdbc.queryForObject("select count(*) from products p"+(filter.isEmpty()?"":" where lower(p.name) like ? or lower(p.sku) like ?"),Long.class,filter.isEmpty()?new Object[]{}:new Object[]{"%"+q.toLowerCase()+"%","%"+q.toLowerCase()+"%"}));}
 @PostMapping @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_MANAGER')") public Map<String,Object> create(@Valid @RequestBody ProductRequest r){var id=UUID.randomUUID();jdbc.update("insert into products(id,sku,barcode,name,description,category_id,brand_id,unit_id,reorder_level) values (?,?,?,?,?,?,?,?,?)",id,r.sku(),r.barcode(),r.name(),r.description(),r.categoryId(),r.brandId(),r.unitId(),r.reorderLevel());jdbc.update("insert into product_prices(product_id,currency_code,amount) values (?, 'USD', ?), (?, 'KHR', ?)",id,r.usdPrice(),id,r.khrPrice());return Map.of("id",id,"sku",r.sku(),"name",r.name());}
 @PutMapping("/{id}") @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_MANAGER')") public Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody ProductRequest r){
  if(jdbc.queryForList("select id from products where id=? for update",id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");
  jdbc.update("update products set sku=?,barcode=?,name=?,description=?,category_id=?,brand_id=?,unit_id=?,reorder_level=? where id=?",r.sku().trim(),r.barcode(),r.name().trim(),r.description(),r.categoryId(),r.brandId(),r.unitId(),r.reorderLevel(),id);
  var current=jdbc.queryForList("select currency_code,amount from product_prices where product_id=? and effective_to is null for update",id);
  Map<String,BigDecimal> prices=new HashMap<>();for(var row:current)prices.put((String)row.get("currency_code"),(BigDecimal)row.get("amount"));
  if(!samePrice(r.usdPrice(),prices.get("USD"))||!samePrice(r.khrPrice(),prices.get("KHR"))){
   jdbc.update("update product_prices set effective_to=now() where product_id=? and effective_to is null",id);
   jdbc.update("insert into product_prices(product_id,currency_code,amount) values (?, 'USD', ?), (?, 'KHR', ?)",id,r.usdPrice(),id,r.khrPrice());
  }
  return Map.of("id",id,"sku",r.sku().trim(),"name",r.name().trim());
 }
 @PatchMapping("/{id}/status") @Transactional @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_MANAGER')") public Map<String,Object> status(@PathVariable UUID id,@Valid @RequestBody StatusRequest request){int updated=jdbc.update("update products set active=? where id=?",request.active(),id);if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found");return Map.of("id",id,"active",request.active());}
 public record StatusRequest(@NotNull Boolean active){}
 private boolean samePrice(BigDecimal requested,BigDecimal current){return current!=null&&requested.compareTo(current)==0;}
 @GetMapping("/lookup") public List<Map<String,Object>> lookup(@RequestParam String query){String q="%"+query.toLowerCase()+"%";return jdbc.queryForList("select p.id,p.sku,p.barcode,p.name,p.unit_id,coalesce(max(pp.amount) filter(where pp.currency_code='USD'),0) usd_price,coalesce(max(pp.amount) filter(where pp.currency_code='KHR'),0) khr_price from products p left join product_prices pp on pp.product_id=p.id and pp.effective_to is null where p.active and (lower(p.name) like ? or lower(p.sku) like ? or p.barcode like ?) group by p.id limit 20",q,q,q);}
}



