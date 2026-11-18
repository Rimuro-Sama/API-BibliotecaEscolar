package io.github.rimurosama.biblioteca.api.erro;

import org.springframework.http.HttpStatus;

/** Exceções de negócio, traduzidas em respostas HTTP pelo {@link TratadorDeErros}. */
public final class Erros {
    private Erros() {
    }

    public abstract static class ErroDeNegocio extends RuntimeException {
        private final HttpStatus status;

        protected ErroDeNegocio(HttpStatus status, String mensagem) {
            super(mensagem);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    /** 404: o recurso pedido não existe. */
    public static class NaoEncontrado extends ErroDeNegocio {
        public NaoEncontrado(String recurso, Object id) {
            super(HttpStatus.NOT_FOUND, recurso + " não encontrado(a): " + id);
        }
    }

    /** 409: a operação conflita com o estado atual (regra de negócio ou dado duplicado). */
    public static class Conflito extends ErroDeNegocio {
        public Conflito(String mensagem) {
            super(HttpStatus.CONFLICT, mensagem);
        }
    }
}
