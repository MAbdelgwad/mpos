package com.sendw.mpos.messaging.application;
public interface MailTransport { void send(MailConfig config,String recipient,String subject,String text); String encrypt(String secret); }
