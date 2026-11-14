package com.retail.dashboard;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
 private final JdbcTemplate jdbc;
 public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/summary") public Map<String,Object> summary(Authentication auth) {
   boolean global=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_SUPER_ADMIN"));
   List<UUID> branches=global?jdbc.queryForList("select id from branches",UUID.class):jdbc.queryForList("select b.id from branches b join user_branches ub on ub.branch_id=b.id join users u on u.id=ub.user_id where u.username=?",UUID.class,auth.getName());
   if(branches.isEmpty()&&!global) return empty();
   String in=global?"": " and branch_id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")";
   List<Object> params=new ArrayList<>(); if(!global)params.addAll(branches);
   Map<String,Object> totals=new LinkedHashMap<>();
   for(String c:List.of("USD","KHR")) { var p=new ArrayList<>(params); p.add(c); var row=jdbc.queryForMap("select coalesce(sum(total),0) as total, count(*) as tx from sales where status in ('COMPLETED','PARTIALLY_REFUNDED','REFUNDED')"+in+" and currency_code=?",p.toArray()); totals.put(c,Map.of("sales",row.get("total"),"transactions",row.get("tx"))); }
   var purchases=new LinkedHashMap<String,Object>(); for(String c:List.of("USD","KHR")){var p=new ArrayList<>(params);p.add(c);purchases.put(c,jdbc.queryForObject("select coalesce(sum(pol.ordered_quantity*pol.unit_cost),0) from purchase_orders po join purchase_order_lines pol on pol.purchase_order_id=po.id where po.status<>'CANCELLED'"+(global?"":" and po.warehouse_location_id in (select id from locations where branch_id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+"))")+" and po.currency_code=?", global?new Object[]{c}:concat(branches,c),BigDecimal.class));}
   long low=jdbc.queryForObject("select count(*) from stock_balances sb join products p on p.id=sb.product_id join locations l on l.id=sb.location_id where p.active=true and sb.quantity<=p.reorder_level"+(global?"":" and l.branch_id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")"),global?new Object[]{}:branches.toArray(),Long.class);
   var recent=global?jdbc.queryForList("select invoice_no,currency_code,total,completed_at from sales order by completed_at desc limit 8"):jdbc.queryForList("select invoice_no,currency_code,total,completed_at from sales where branch_id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+") order by completed_at desc limit 8",branches.toArray());
   var salesTrend=new LinkedHashMap<String,Object>();for(String c:List.of("USD","KHR")){String trendSql="select to_char(date_trunc('day',completed_at),'YYYY-MM-DD') as \"day\",coalesce(sum(total),0) total from sales where currency_code=? and completed_at>=current_date-6"+(global?"":" and branch_id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")")+" group by 1 order by 1";Object[] trendArgs;if(global)trendArgs=new Object[]{c};else{var a=new ArrayList<Object>();a.add(c);a.addAll(branches);trendArgs=a.toArray();}salesTrend.put(c,jdbc.queryForList(trendSql,trendArgs));}
   String branchSql="select b.code,b.name,coalesce(sum(s.total) filter(where s.currency_code='USD'),0) usd_sales,coalesce(sum(s.total) filter(where s.currency_code='KHR'),0) khr_sales,count(s.id) transactions from branches b left join sales s on s.branch_id=b.id and s.status in ('COMPLETED','PARTIALLY_REFUNDED','REFUNDED')"+(global?"":" where b.id in ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")")+" group by b.id order by b.name";var branchPerformance=global?jdbc.queryForList(branchSql):jdbc.queryForList(branchSql,branches.toArray());
   return Map.of("sales",totals,"purchases",purchases,"lowStockCount",low,"recentSales",recent,"branchCount",global?jdbc.queryForObject("select count(*) from branches",Long.class):branches.size(),"salesTrend",salesTrend,"branchPerformance",branchPerformance);
 }
 private Object[] concat(List<UUID> ids,String currency){var x=new ArrayList<Object>(ids);x.add(currency);return x.toArray();}
 private Map<String,Object> empty(){return Map.of("sales",Map.of("USD",Map.of("sales",0,"transactions",0),"KHR",Map.of("sales",0,"transactions",0)),"purchases",Map.of("USD",0,"KHR",0),"lowStockCount",0,"recentSales",List.of(),"branchCount",0);}
}


