package io.github.rimurosama.biblioteca.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI documentacao() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Biblioteca Escolar")
                        .version("1.0.0")
                        .description("""
                                Acervo, leitores, empréstimos, renovações e multas de uma biblioteca escolar.

                                Regras: prazo de 14 dias; 1 renovação de +7 dias (só sem atraso); máximo de 3 empréstimos ativos por leitor; \
                                multa de R$ 0,50 por dia de atraso (valores em centavos); leitor com multa pendente ou empréstimo atrasado \
                                não pode pegar outro livro. A consulta ao catálogo é pública; o resto exige autenticação (HTTP Basic)."""))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
