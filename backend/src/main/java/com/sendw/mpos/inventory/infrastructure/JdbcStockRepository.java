package com.sendw.mpos.inventory.infrastructure;

import com.sendw.mpos.inventory.application.StockRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStockRepository implements StockRepository {
    private final JdbcTemplate db;
    public JdbcStockRepository(JdbcTemplate db) { this.db = db; }

    @Override public BigDecimal lockBalance(long warehouse, long product) {
        db.update("INSERT INTO stock_balances(warehouse_id,product_id) VALUES (?,?) ON CONFLICT DO NOTHING", warehouse, product);
        return db.queryForObject("SELECT quantity FROM stock_balances WHERE warehouse_id=? AND product_id=? FOR UPDATE", BigDecimal.class, warehouse, product);
    }

    @Override public boolean hasMovements(long warehouse, long product) {
        return db.queryForObject("SELECT count(*) FROM stock_movements WHERE warehouse_id=? AND product_id=?", Long.class, warehouse, product) > 0;
    }

    @Override public void createOperation(UUID id, String kind, Long purchase, String reason, long actor) {
        db.update("INSERT INTO stock_operations(id,kind,purchase_id,reason,created_by) VALUES (?,?,?,?,?)", id, kind, purchase, reason, actor);
    }

    @Override public void recordMovement(UUID operation, long warehouse, long product, BigDecimal delta, BigDecimal balance) {
        db.update("UPDATE stock_balances SET quantity=? WHERE warehouse_id=? AND product_id=?", balance, warehouse, product);
        db.update("INSERT INTO stock_movements(operation_id,warehouse_id,product_id,quantity_delta,quantity_after) VALUES (?,?,?,?,?)", operation, warehouse, product, delta, balance);
    }
}
