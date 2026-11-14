package com.sendw.mpos.payments.infrastructure;
import com.sendw.mpos.payments.application.PaymentRepository;
import com.sendw.mpos.shared.domain.Validation;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Repository
public class JdbcPaymentRepository implements PaymentRepository {
    private final JdbcTemplate db;
    public JdbcPaymentRepository(JdbcTemplate db){this.db=db;}
    private String column(String type){return switch(type){case "sales"->"sale_id";case "purchases"->"purchase_id";default->throw Validation.missing();};}
    private String table(String type){column(type);return type;}
    public Document document(String type,long id,boolean lock){
        var rows=db.queryForList("SELECT status,grand_total FROM "+table(type)+" WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw Validation.missing();var row=rows.getFirst();return new Document(((Number)row.get("status")).intValue(),(BigDecimal)row.get("grand_total"));
    }
    public BigDecimal paid(String type,long id){return db.queryForObject("SELECT COALESCE(sum(amount),0) FROM document_payments WHERE "+column(type)+"=? AND voided_at IS NULL",BigDecimal.class,id);}
    private Map<String,Object> dates(Map<String,Object> row){row.replaceAll((k,v)->v instanceof java.sql.Date d?d.toLocalDate().toString():v instanceof java.sql.Timestamp t?t.toInstant().toString():v);return row;}
    public List<Map<String,Object>> list(String type,long id){return db.queryForList("SELECT * FROM document_payments WHERE "+column(type)+"=? ORDER BY id DESC",id).stream().map(this::dates).toList();}
    public Map<String,Object> find(String type,long document,long payment){var rows=db.queryForList("SELECT * FROM document_payments WHERE "+column(type)+"=? AND id=?",document,payment);if(rows.isEmpty())throw Validation.missing();return dates(rows.getFirst());}
    public long insert(String type,long id,BigDecimal amount,LocalDate date,String method,String reference,String notes,long actor){return db.queryForObject("INSERT INTO document_payments("+column(type)+",amount,date,method,reference,notes,created_by) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,id,amount,java.sql.Date.valueOf(date),method,reference,notes,actor);}
    public void voidPayment(long id,String reason,long actor){db.update("UPDATE document_payments SET voided_at=now(),voided_by=?,void_reason=? WHERE id=? AND voided_at IS NULL",actor,reason,id);}
}
