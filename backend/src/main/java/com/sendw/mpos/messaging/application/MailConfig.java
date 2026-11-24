package com.sendw.mpos.messaging.application;
public record MailConfig(String host,int port,String username,String secret,String security,String from_email,String from_name,String public_url,boolean enabled,boolean verify_new_users) {}
