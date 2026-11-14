package com.sendw.mpos;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, IdentityService identity) throws Exception {
        // These endpoints accept explicit Authorization headers only, never session cookies.
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/login").permitAll()
                .requestMatchers("/api/warehouses", "/api/warehouses/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> error(res,401,"Unauthenticated"))
                .accessDeniedHandler((req,res,ex) -> error(res,403,"Forbidden")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    String header = request.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ") && header.length() <= 256) {
                        identity.authenticate(header.substring(7)).ifPresent(user -> {
                            var auth = new UsernamePasswordAuthenticationToken(user, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
                    }
                    chain.doFilter(request, response);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }

    private static void error(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
