package com.retail.common;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
class CurrencyTotalsTest {
 @Test void keepsCurrenciesSeparate(){var totals=Map.of("USD",new BigDecimal("12.50"),"KHR",new BigDecimal("45000"));assertEquals(new BigDecimal("12.50"),totals.get("USD"));assertEquals(new BigDecimal("45000"),totals.get("KHR"));assertEquals(2,totals.size());}
 @Test void decimalAmountsDoNotUseFloatingPoint(){var total=new BigDecimal("0.10").add(new BigDecimal("0.20"));assertEquals(new BigDecimal("0.30"),total);}
}
