package cr.ac.entrenos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record CurrentUser(long id, String name, String email, String role) {}

@Component
class Tokens {
    private record Entry(CurrentUser user, Instant expires) {}
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    String issue(CurrentUser user) {
        tokens.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(user, Instant.now().plusSeconds(8 * 3600))); return token;
    }
    CurrentUser resolve(String token) {
        Entry entry = tokens.get(token);
        if (entry == null) return null;
        if (entry.expires().isBefore(Instant.now())) { tokens.remove(token); return null; }
        return entry.user();
    }
    void revoke(String token) { tokens.remove(token); }
    void revokeUser(long userId) { tokens.entrySet().removeIf(e -> e.getValue().user().id() == userId); }
}

@Component
class TokenFilter extends OncePerRequestFilter {
    private final Tokens tokens;
    TokenFilter(Tokens tokens) { this.tokens = tokens; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            CurrentUser user = tokens.resolve(header.substring(7));
            if (user != null) SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
        }
        chain.doFilter(request, response);
    }
}

@Configuration
class Security {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, TokenFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register-coach").permitAll().anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
