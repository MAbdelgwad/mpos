package com.sendw.mpos.identity.application;
import com.sendw.mpos.messaging.application.MailService;
import com.sendw.mpos.shared.domain.Validation;
import java.util.*;
@org.springframework.stereotype.Service
public class VerificationService {
 private final VerificationRepository repository;private final MailService mail;
 public VerificationService(VerificationRepository r,MailService m){repository=r;mail=m;}
 public void newAccount(long id){if(mail.config().verify_new_users())issue(id);}
 private void issue(long id){var u=repository.lockUser(id);if(repository.recentlySent(id))throw Validation.invalid("Wait one minute before resending");byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);String url=mail.publicUrl()+"/#/verify-email/"+token;repository.issue(id,(String)u.get("email"),IdentityService.digest(token));mail.send((String)u.get("email"),"MPOS — تأكيد البريد / Verify email","لتأكيد بريدك افتح الرابط واضغط تأكيد. الرابط صالح لمدة 24 ساعة.\nOpen this link and confirm your email within 24 hours:\n"+url);}
 @org.springframework.transaction.annotation.Transactional public void resend(long id){Permissions.require("manage_users");var u=repository.lockUser(id);if(Boolean.TRUE.equals(u.get("email_verified")))throw Validation.invalid("Email is already verified");issue(id);}
 @org.springframework.transaction.annotation.Transactional public void confirm(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}")||!repository.consume(IdentityService.digest(token)))throw Validation.invalid("Verification link is invalid, expired or already used");}
}
