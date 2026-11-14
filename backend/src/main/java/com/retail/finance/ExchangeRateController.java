package com.retail.finance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {
 private final JdbcTemplate jdbc;public ExchangeRateController(JdbcTemplate j){jdbc=j;}
 public record Rate(@Pattern(regexp="USD|KHR") String baseCurrency,@Pattern(regexp="USD|KHR") String quoteCurrency,@NotNull @DecimalMin("0.0000000001") BigDecimal rate,@NotNull Instant effectiveAt,String source){}
 @GetMapping public List<Map<String,Object>> list(){return jdbc.queryForList("select id,base_currency,quote_currency,rate,effective_at,source from exchange_rates order by effective_at desc limit 200");}
 @PostMapping @PreAuthorize("hasRole('SUPER_ADMIN')") public Map<String,Object> create(@Valid @RequestBody Rate r,Authentication a){if(r.baseCurrency().equals(r.quoteCurrency()))throw new IllegalArgumentException("Currencies must differ");UUID user=jdbc.queryForObject("select id from users where username=?",UUID.class,a.getName()),id=UUID.randomUUID();jdbc.update("insert into exchange_rates(id,base_currency,quote_currency,rate,effective_at,source,entered_by) values (?,?,?,?,?,?,?)",id,r.baseCurrency(),r.quoteCurrency(),r.rate(),r.effectiveAt(),r.source(),user);return Map.of("id",id,"baseCurrency",r.baseCurrency(),"quoteCurrency",r.quoteCurrency(),"rate",r.rate(),"effectiveAt",r.effectiveAt());}
}
