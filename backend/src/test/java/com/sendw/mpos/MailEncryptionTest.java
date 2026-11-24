package com.sendw.mpos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sendw.mpos.messaging.infrastructure.SmtpTransport;
class MailEncryptionTest {
 @Test void credentialsUseRandomizedAuthenticatedEncryptionAndRequireKey() throws Exception {
  var transport=new SmtpTransport(java.util.Base64.getEncoder().encodeToString(new byte[32]));
  String first=transport.encrypt("app-secret"),second=transport.encrypt("app-secret");assertNotEquals(first,second);assertFalse(first.contains("app-secret"));
  var method=SmtpTransport.class.getDeclaredMethod("decrypt",String.class);method.setAccessible(true);assertEquals("app-secret",method.invoke(transport,first));
  assertThrows(Exception.class,()->method.invoke(transport,first.substring(0,first.length()-3)+"AAA"));
  assertThrows(com.sendw.mpos.shared.domain.BusinessException.class,()->new SmtpTransport("").encrypt("secret"));
 }
}
