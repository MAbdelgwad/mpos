package com.sendw.mpos.messaging.application;
import com.sendw.mpos.identity.application.Permissions;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
@org.springframework.stereotype.Service
public class MailService {
 private final MailRepository repository;private final MailTransport transport;
 public MailService(MailRepository repository,MailTransport transport){this.repository=repository;this.transport=transport;}
 public MailConfig config(){return repository.get();}
 public Map<String,Object> settings(){Permissions.require("manage_setting");var c=config();return Map.of("data",Map.of("host",c.host(),"port",c.port(),"username",c.username(),"password_set",!c.secret().isEmpty(),"security",c.security(),"from_email",c.from_email(),"from_name",c.from_name(),"public_url",c.public_url(),"enabled",c.enabled(),"verify_new_users",c.verify_new_users()));}
 private String field(Map<String,Object>b,String k,int max){String v=Objects.toString(b.get(k),"").trim();if(v.length()>max||v.contains("\r")||v.contains("\n"))throw Validation.invalid("Invalid "+k);return v;}
 public static String email(String value){if(value==null||value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw Validation.invalid("Invalid email address");return value;}
 public Map<String,Object> save(Map<String,Object>b){Permissions.require("manage_setting");var old=config();String host=field(b,"host",255),security=field(b,"security",10),from=field(b,"from_email",254),url=field(b,"public_url",500);int port;try{port=Integer.parseInt(Objects.toString(b.get("port")));}catch(Exception e){throw Validation.invalid("Invalid SMTP port");}
  if(port<1||port>65535||!Set.of("starttls","ssl").contains(security))throw Validation.invalid("Use STARTTLS or SSL and a valid port");
  if(!url.isBlank()){try{var u=java.net.URI.create(url);if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException();url=url.replaceAll("/+$","");}catch(Exception e){throw Validation.invalid("Public URL must be an http(s) origin without a path");}}
  boolean enabled=Boolean.TRUE.equals(b.get("enabled")),verify=Boolean.TRUE.equals(b.get("verify_new_users"));String password=Objects.toString(b.get("password"),"");if(password.length()>1024)throw Validation.invalid("Password too long");String secret=password.isEmpty()?old.secret():transport.encrypt(password);
  if(enabled){email(from);if(host.isBlank()||!host.matches("[A-Za-z0-9.:-]+"))throw Validation.invalid("Invalid SMTP host");}
  if(verify&&(!enabled||url.isBlank()))throw Validation.invalid("Configure email and public URL before enabling verification");
  repository.save(new MailConfig(host,port,field(b,"username",254),secret,security,from,field(b,"from_name",100),url,enabled,verify));return settings();
 }
 public void send(String recipient,String subject,String text){var c=config();if(!c.enabled())throw Validation.invalid("Email is not enabled");transport.send(c,email(recipient),subject,text);}
 public void test(String to){Permissions.require("manage_setting");send(to,"MPOS — اختبار البريد / Email test","تم إعداد البريد بنجاح.\nMPOS email settings test.");}
 public String publicUrl(){String url=config().public_url();if(url.isBlank())throw Validation.invalid("Set the public application URL in email settings first");return url;}
}
