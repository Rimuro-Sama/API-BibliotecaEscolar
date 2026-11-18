package io.github.rimurosama.biblioteca.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança da API: HTTP Basic, sem sessão, com UM usuário (o bibliotecário) configurado por variáveis de ambiente.
 *
 * Público: consulta do catálogo (GET /api/livros/**), health check e a documentação (Swagger UI).
 * Todo o resto exige autenticação, porque envolve dados pessoais dos leitores e operações de empréstimo.
 *
 * Não há senha padrão: se BIBLIOTECA_ADMIN_USER / BIBLIOTECA_ADMIN_PASSWORD não forem definidas, a aplicação não sobe.
 * Use HTTPS na frente da API em produção: no HTTP Basic as credenciais viajam a cada requisição.
 */
@Configuration
public class SegurancaConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // API sem sessão nem cookies: não há o que forjar entre sites
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/livros", "/api/livros/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(PasswordEncoder encoder,
                                @Value("${biblioteca.admin.user}") String usuario,
                                @Value("${biblioteca.admin.password}") String senha) {
        if (usuario.isBlank() || senha.isBlank()) {
            throw new IllegalStateException("Defina BIBLIOTECA_ADMIN_USER e BIBLIOTECA_ADMIN_PASSWORD.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(usuario).password(encoder.encode(senha)).roles("BIBLIOTECARIO").build());
    }
}
