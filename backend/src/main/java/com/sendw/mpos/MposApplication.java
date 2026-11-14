package com.sendw.mpos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class MposApplication {
    public static void main(String[] args) { SpringApplication.run(MposApplication.class, args); }
}
