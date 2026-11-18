package io.github.rimurosama.biblioteca.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Falha cedo e com mensagem clara se faltar configuração de banco ou de acesso.
 *
 * Roda antes de qualquer bean ser criado, ou seja, antes de qualquer tentativa de conexão. Sem isso, a falta de
 * DB_USER aparecia como um erro confuso do PostgreSQL ("role ${DB_USER} does not exist").
 * Os valores em si nunca são mostrados, só os nomes das variáveis.
 */
@Component
class ConfiguracaoObrigatoria implements BeanFactoryPostProcessor, EnvironmentAware {
    private static final List<String[]> OBRIGATORIAS = List.of(
            new String[]{"spring.datasource.username", "DB_USER"},
            new String[]{"spring.datasource.password", "DB_PASSWORD"},
            new String[]{"biblioteca.admin.user", "BIBLIOTECA_ADMIN_USER"},
            new String[]{"biblioteca.admin.password", "BIBLIOTECA_ADMIN_PASSWORD"});

    private Environment ambiente;

    @Override
    public void setEnvironment(Environment environment) {
        this.ambiente = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> faltando = OBRIGATORIAS.stream()
                .filter(p -> {
                    String valor = ambiente.getProperty(p[0]);
                    return valor == null || valor.isBlank();
                })
                .map(p -> p[1])
                .toList();
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("Configuração obrigatória ausente. Defina as variáveis de ambiente: "
                    + String.join(", ", faltando) + " (veja .env.example).");
        }
    }
}
