package com.sendw.mpos.messaging.infrastructure;
import com.sendw.mpos.messaging.application.*;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.mail.javamail.*;
@Component
public class SmtpTransport implements MailTransport {
 private final String key;
 public SmtpTransport(@Value("${MPOS_MAIL_KEY:}") String key){this.key=key;}
 private byte[] key(){try{byte[] bytes=Base64.getDecoder().decode(key);if(bytes.length!=32)throw new IllegalArgumentException();return bytes;}catch(Exception e){throw Validation.invalid("Mail encryption key is missing. Run scripts/setup.py and restart the API.");}}
 public String encrypt(String secret){try{byte[] iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,iv));return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(c.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(com.sendw.mpos.shared.domain.BusinessException e){throw e;}catch(Exception e){throw new IllegalStateException("Cannot encrypt mail credentials");}}
 private String decrypt(String secret)throws Exception{String[] parts=secret.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),java.nio.charset.StandardCharsets.UTF_8);}
 public void send(MailConfig c,String recipient,String subject,String text){
  try{var sender=new JavaMailSenderImpl();sender.setHost(c.host());sender.setPort(c.port());sender.setUsername(c.username());if(!c.secret().isBlank())sender.setPassword(decrypt(c.secret()));
   var p=sender.getJavaMailProperties();p.setProperty("mail.smtp.auth",String.valueOf(!c.username().isBlank()));p.setProperty("mail.smtp.connectiontimeout","5000");p.setProperty("mail.smtp.timeout","10000");p.setProperty("mail.smtp.writetimeout","10000");p.setProperty("mail.smtp.ssl.checkserveridentity","true");p.setProperty("mail.smtp.starttls.enable",String.valueOf(c.security().equals("starttls")));p.setProperty("mail.smtp.starttls.required",String.valueOf(c.security().equals("starttls")));p.setProperty("mail.smtp.ssl.enable",String.valueOf(c.security().equals("ssl")));
   var m=sender.createMimeMessage();var h=new MimeMessageHelper(m,"UTF-8");h.setFrom(c.from_email(),c.from_name());h.setTo(recipient);h.setSubject(subject);h.setText(text,false);sender.send(m);
  }catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Email delivery failed. Check SMTP settings and connection.");}
 }
}
