package com.sendw.mpos.inventory.application;
import com.sendw.mpos.identity.application.IdentityService;
import com.sendw.mpos.shared.domain.Requests;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import java.math.BigDecimal;
import com.sendw.mpos.inventory.domain.StockPolicy;
import org.springframework.transaction.annotation.*;
import org.springframework.stereotype.Service;
@Service
public class StockService implements StockOperations {
    private record Key(long warehouse,long product) implements Comparable<Key>{public int compareTo(Key other){int n=Long.compare(warehouse,other.warehouse);return n==0?Long.compare(product,other.product):n;}}
    private final StockRepository repository;private final IdentityService identity;
    public StockService(StockRepository repository,IdentityService identity){this.repository=repository;this.identity=identity;}
    // All callers hold a database transaction. Lock keys in stable order for transfers and multi-line receipts.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID apply(String kind,Long purchase,String reason,List<Delta> deltas){
        var totals=new TreeMap<Key,BigDecimal>();for(var d:deltas)totals.merge(new Key(d.warehouse(),d.product()),d.quantity(),BigDecimal::add);
        var balances=new TreeMap<Key,BigDecimal>();
        for(var key:totals.keySet()){
            BigDecimal quantity=repository.lockBalance(key.warehouse(),key.product());
            if(kind.equals("OPENING") && repository.hasMovements(key.warehouse(),key.product()))throw Requests.conflict("Opening balance is only allowed before the first movement");
            BigDecimal next=StockPolicy.nextBalance(quantity,totals.get(key),key.warehouse(),key.product());
            balances.put(key,next);
        }
        UUID operation=UUID.randomUUID();repository.createOperation(operation,kind,purchase,reason,identity.current().id());
        for(var entry:totals.entrySet()){
            if(entry.getValue().signum()==0)throw Validation.invalid("Zero stock movement");var key=entry.getKey();
            repository.recordMovement(operation,key.warehouse(),key.product(),entry.getValue(),balances.get(key));
        }
        return operation;
    }
}
