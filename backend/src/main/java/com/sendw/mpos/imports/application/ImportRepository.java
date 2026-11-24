package com.sendw.mpos.imports.application;
import java.util.*;
public interface ImportRepository {
 Map<String,Object> lookups();
 List<Map<String,Object>> partners(); Map<String,Object> partner(long id);long savePartner(Long id,Map<String,Object>b);
 List<Map<String,Object>> list(int page,String search);long count(String search);Map<String,Object> shipment(long id,boolean lock);long saveShipment(Long id,Map<String,Object>b,long actor);void replaceContainers(long id,List<Map<String,Object>> rows);
 List<Map<String,Object>> containers(long id);Map<String,Object> product(long id);void receive(long id);void cancel(long id);
 List<Map<String,Object>> expenses(long id);long addExpense(long shipment,Map<String,Object>b);void removeExpense(long shipment,long expense);
 void addFile(UUID id,long shipment,Long expense,String kind,ImportFile file);List<Map<String,Object>> files(long shipment);ImportFile file(long shipment,UUID file);void removeFile(long shipment,UUID file);
 void allocate(long container,long sale,long pieces);List<Map<String,Object>> sales(long shipment);void setSaleCurrency(long id,String currency,String symbol);
 record ImportFile(String filename,String mime,byte[] content){}
}
