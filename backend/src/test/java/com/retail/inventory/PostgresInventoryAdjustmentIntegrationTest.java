package com.retail.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Runs only when RMS_POSTGRES_TESTS=true and the datasource points at a newly
 * created, isolated PostgreSQL test schema. It intentionally retains fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "RMS_POSTGRES_TESTS", matches = "true")
class PostgresInventoryAdjustmentIntegrationTest {
    @Autowired private InventoryController inventory;
    @Autowired private JdbcTemplate jdbc;

    private final Authentication admin = UsernamePasswordAuthenticationToken.authenticated(
        "itadmin", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void subtractsFromExistingBalanceAndWritesMovementAndAudit() {
        Fixture fixture = fixture(10);

        var result = adjust(fixture, -3, "pg-existing-minus-three");

        assertEquals(7, result.get("quantity"));
        assertEquals(7, balance(fixture));
        assertEquals(1, movementCount(fixture, "pg-existing-minus-three"));
        assertEquals(1, auditedMovementCount(fixture, "pg-existing-minus-three"));
    }

    @Test
    void rejectsAdjustmentBelowZeroAndRollsBackLedgerAndAudit() {
        Fixture fixture = fixture(3);

        assertThrows(IllegalStateException.class, () -> adjust(fixture, -5, "pg-insufficient-minus-five"));

        assertEquals(3, balance(fixture));
        assertEquals(0, movementCount(fixture, "pg-insufficient-minus-five"));
        assertEquals(0, auditedMovementCount(fixture, "pg-insufficient-minus-five"));
    }

    @Test
    void addsPositiveAdjustmentToExistingBalance() {
        Fixture fixture = fixture(10);

        var result = adjust(fixture, 4, "pg-existing-plus-four");

        assertEquals(14, result.get("quantity"));
        assertEquals(14, balance(fixture));
        assertEquals(1, movementCount(fixture, "pg-existing-plus-four"));
        assertEquals(1, auditedMovementCount(fixture, "pg-existing-plus-four"));
    }

    @Test
    void missingBalanceRejectsDecrementButPositiveAdjustmentCreatesIt() {
        Fixture fixture = fixture(null);

        assertThrows(IllegalStateException.class, () -> adjust(fixture, -1, "pg-missing-minus-one"));
        assertEquals(0, balanceCount(fixture));
        assertEquals(0, movementCount(fixture, "pg-missing-minus-one"));
        assertEquals(0, auditedMovementCount(fixture, "pg-missing-minus-one"));

        var result = adjust(fixture, 5, "pg-missing-plus-five");
        assertEquals(5, result.get("quantity"));
        assertEquals(5, balance(fixture));
        assertEquals(1, movementCount(fixture, "pg-missing-plus-five"));
        assertEquals(1, auditedMovementCount(fixture, "pg-missing-plus-five"));
    }

    @Test
    void concurrentDecrementsCannotOverdrawOrLoseUpdates() throws Exception {
        Fixture fixture = fixture(10);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> concurrentAdjustment(fixture, ready, start, "pg-concurrent-a"));
            Future<Boolean> second = executor.submit(() -> concurrentAdjustment(fixture, ready, start, "pg-concurrent-b"));

            ready.await();
            start.countDown();
            boolean firstSucceeded = first.get();
            boolean secondSucceeded = second.get();

            assertNotEquals(firstSucceeded, secondSucceeded, "exactly one decrement must succeed");
            assertEquals(3, balance(fixture));
            assertEquals(1, movementCountForProduct(fixture));
            assertEquals(1, auditCountForProduct(fixture));
        }
    }

    private Boolean concurrentAdjustment(Fixture fixture, CountDownLatch ready, CountDownLatch start, String reason)
        throws InterruptedException {
        SecurityContextHolder.getContext().setAuthentication(admin);
        ready.countDown();
        start.await();
        try {
            adjust(fixture, -7, reason);
            return true;
        } catch (IllegalStateException insufficientStock) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private java.util.Map<String, Object> adjust(Fixture fixture, int delta, String reason) {
        SecurityContextHolder.getContext().setAuthentication(admin);
        return inventory.adjust(new InventoryController.Adjustment(fixture.locationId(), fixture.productId(), delta, reason), admin);
    }

    private Fixture fixture(Integer initialQuantity) {
        UUID productId = UUID.randomUUID();
        UUID locationId = jdbc.queryForObject(
            "select id from locations where type='CENTRAL_WAREHOUSE' and active=true order by code limit 1", UUID.class);
        UUID unitId = jdbc.queryForObject("select id from units where code='EA'", UUID.class);
        String sku = "PG-ADJUST-" + productId.toString().substring(0, 12);
        jdbc.update("insert into products(id,sku,name,unit_id) values (?,?,?,?)", productId, sku, "PG adjustment fixture", unitId);
        if (initialQuantity != null) {
            jdbc.update("insert into stock_balances(location_id,product_id,quantity) values (?,?,?)", locationId, productId, initialQuantity);
        }
        return new Fixture(locationId, productId);
    }

    private int balance(Fixture fixture) {
        return jdbc.queryForObject("select quantity from stock_balances where location_id=? and product_id=?", Integer.class,
            fixture.locationId(), fixture.productId());
    }

    private int balanceCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from stock_balances where location_id=? and product_id=?", Integer.class,
            fixture.locationId(), fixture.productId());
    }

    private int movementCount(Fixture fixture, String reason) {
        return jdbc.queryForObject("select count(*) from stock_movements where location_id=? and product_id=? and source_type=? and movement_type='ADJUSTMENT'", Integer.class,
            fixture.locationId(), fixture.productId(), reason);
    }

    private int auditedMovementCount(Fixture fixture, String reason) {
        return jdbc.queryForObject("select count(*) from audit_events ae join stock_movements sm on sm.source_id=ae.entity_id where sm.location_id=? and sm.product_id=? and sm.source_type=? and ae.action='STOCK_ADJUSTED'", Integer.class,
            fixture.locationId(), fixture.productId(), reason);
    }

    private int movementCountForProduct(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from stock_movements where location_id=? and product_id=? and movement_type='ADJUSTMENT'", Integer.class,
            fixture.locationId(), fixture.productId());
    }

    private int auditCountForProduct(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from audit_events ae join stock_movements sm on sm.source_id=ae.entity_id where sm.location_id=? and sm.product_id=? and ae.action='STOCK_ADJUSTED'", Integer.class,
            fixture.locationId(), fixture.productId());
    }

    private record Fixture(UUID locationId, UUID productId) {}
}
