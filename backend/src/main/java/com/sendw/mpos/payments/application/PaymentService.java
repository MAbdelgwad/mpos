package com.sendw.mpos.payments.application;
import com.sendw.mpos.identity.application.*;
import com.sendw.mpos.shared.application.RequestDeduplicator;
import com.sendw.mpos.shared.domain.*;
import com.sendw.mpos.payments.domain.Settlement;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PaymentService {
    private final PaymentRepository repository;private final IdentityService identity;private final RequestDeduplicator deduplicator;
    public PaymentService(PaymentRepository repository,IdentityService identity,RequestDeduplicator deduplicator){this.repository=repository;this.identity=identity;this.deduplicator=deduplicator;}
    private void permit(String type){Permissions.require(switch(type){case "sales"->"manage_sale";case "purchases"->"manage_purchase";default->throw Validation.missing();});Permissions.require("manage_payments");}
    private Map<String,Object> resource(Map<String,Object> row){var attrs=new LinkedHashMap<>(row);Object id=attrs.remove("id");return Map.of("id",id,"type","payments","attributes",attrs);}
    // Taking the same parent lock for all payment operations gives consistent summaries and serializes cancellation.
    @Transactional
    public Map<String,Object> list(String type,long id){permit(type);var document=repository.document(type,id,true);var balance=Settlement.of(document.total(),repository.paid(type,id),document.status()==4);return Map.of("data",repository.list(type,id).stream().map(this::resource).toList(),"summary",balance);}
    @Transactional
    public Map<String,Object> create(String type,long id,PaymentRequest request,String key){
        permit(type);var body=request.values();
        return deduplicator.execute("payments:"+type+":"+id,key,body,()->{
            var document=repository.document(type,id,true);
            if(document.status()!=1)throw Requests.conflict("Payments require a completed sale or received purchase");
            var amount=Requests.number(body,"amount",true);if(amount.signum()<=0)throw Validation.invalid("Payment amount must be positive");
            var date=Requests.date(body);var method=Requests.text(body,"method",20,true);
            if(!Set.of("CASH","BANK","CARD").contains(method))throw Validation.invalid("Invalid payment method");
            var reference=Requests.text(body,"reference",120,false);var notes=Requests.text(body,"notes",1000,false);
            Settlement.of(document.total(),repository.paid(type,id),false).accept(amount);
            long payment=repository.insert(type,id,amount,date,method,reference,notes,identity.current().id());
            return Map.of("data",resource(repository.find(type,id,payment)));
        });
    }
    @Transactional
    public Map<String,Object> voidPayment(String type,long document,long payment,String reason){
        permit(type);repository.document(type,document,true);var old=repository.find(type,document,payment);
        String cleaned=Requests.text(Collections.singletonMap("reason",reason),"reason",1000,true);
        if(old.get("voided_at")==null)repository.voidPayment(payment,cleaned,identity.current().id());
        return Map.of("data",resource(repository.find(type,document,payment)));
    }
}
