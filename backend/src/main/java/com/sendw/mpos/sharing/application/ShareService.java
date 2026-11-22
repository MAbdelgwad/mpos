package com.sendw.mpos.sharing.application;
import com.sendw.mpos.identity.application.*;
import com.sendw.mpos.messaging.application.MailService;
import com.sendw.mpos.sales.application.SaleService;
import com.sendw.mpos.purchasing.application.PurchaseService;
import com.sendw.mpos.settings.application.SettingsRepository;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import java.time.*;
import tools.jackson.databind.ObjectMapper;
@org.springframework.stereotype.Service
public class ShareService {
 private final ShareRepository repository;private final SaleService sales;private final PurchaseService purchases;private final MailService mail;private final SettingsRepository settings;private final IdentityService identity;private final ObjectMapper json;
 public ShareService(ShareRepository r,SaleService s,PurchaseService p,MailService m,SettingsRepository st,IdentityService i,ObjectMapper j){repository=r;sales=s;purchases=p;mail=m;settings=st;identity=i;json=j;}
 private void permit(String kind){if(!Set.of("sales","purchases").contains(kind))throw Validation.missing();Permissions.require(kind.equals("sales")?"manage_sale":"manage_purchase");}
 private Map<String,Object> select(Map<?,?> source,String...keys){var r=new LinkedHashMap<String,Object>();for(String k:keys)if(source.containsKey(k))r.put(k,source.get(k));return r;}
 @org.springframework.transaction.annotation.Transactional
 public Map<String,Object> create(String kind,long id,int days,String email){permit(kind);if(days<1||days>30)throw Validation.invalid("Link expiry must be 1–30 days");if(email!=null&&!email.isBlank())MailService.email(email);
  var doc=(Map<?,?>)(kind.equals("sales")?sales.show(id):purchases.show(id)).get("data");var attrs=(Map<?,?>)doc.get("attributes");
  var clean=select(attrs,"reference_code","date","customer_name","supplier_name","warehouse_name","status","discount","tax_rate","tax_amount","shipping","grand_total","paid_amount","due_amount","currency_code","currency_symbol");
  String linesKey=kind.equals("sales")?"sale_items":"purchase_items";
  clean.put(linesKey,((List<?>)attrs.get(linesKey)).stream().map(x->select((Map<?,?>)x,"name","code","quantity","unit_name",kind.equals("sales")?"product_price":"product_cost","discount_amount","tax_amount","tax_value","tax_type","sub_total")).toList());
  var style=new LinkedHashMap<String,String>();settings.settings().forEach((k,v)->{if(k.startsWith("invoice_")||Set.of("company_name","currency_symbol").contains(k))style.put(k,v);});
  style.put("invoice_show_notes","false");
  Instant expiry=Instant.now().plus(days,java.time.temporal.ChronoUnit.DAYS);var snapshot=Map.of("document",Map.of("attributes",clean),"type",kind,"settings",style,"created_at",Instant.now().toString(),"expires_at",expiry.toString());
  byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);UUID share=UUID.randomUUID();String url=mail.publicUrl()+"/#/shared-invoice/"+token;
  repository.create(share,kind,id,IdentityService.digest(token),json.writeValueAsString(snapshot),expiry,identity.current().id());
  if(email!=null&&!email.isBlank())mail.send(email,"MPOS — فاتورة / Invoice "+clean.get("reference_code"),"رابط الفاتورة للعرض فقط — نسخة وقت المشاركة، صالح حتى "+expiry+"\nRead-only invoice snapshot, valid until "+expiry+"\n"+url);
  return Map.of("data",Map.of("id",share,"url",url,"expires_at",expiry.toString()));
 }
 public Map<String,Object> list(String kind,long id){permit(kind);return Map.of("data",repository.list(kind,id));}
 public void revoke(String kind,long id,UUID share){permit(kind);repository.revoke(kind,id,share);}
 public Object view(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw Validation.missing();return json.readValue(repository.snapshot(IdentityService.digest(token)).orElseThrow(Validation::missing),Map.class);}
}
