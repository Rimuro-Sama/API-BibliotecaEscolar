package io.github.rimurosama.biblioteca.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    /**
     * Relógio injetável: o "hoje" das regras de prazo e multa vem daqui, no fuso da escola.
     * Os testes substituem este bean por um relógio controlável, para simular atrasos sem esperar dias.
     */
    @Bean
    Clock relogio() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
