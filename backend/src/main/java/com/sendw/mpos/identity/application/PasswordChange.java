package com.sendw.mpos.identity.application;
/** JSON property names remain compatible with the existing React client. */
public record PasswordChange(String current_password,String password,String password_confirmation) {}
