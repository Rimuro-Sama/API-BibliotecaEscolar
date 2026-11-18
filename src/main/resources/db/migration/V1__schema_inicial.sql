-- Esquema inicial da biblioteca escolar.
-- Evolução do modelo do repositório Biblioteca-BancoDeDados (leitor, livro, emprestimo), agora com
-- exemplares físicos, renovações e multas, e com as regras que dependem de integridade garantidas pelo banco.

CREATE TABLE leitor (
    id        BIGSERIAL PRIMARY KEY,
    nome      VARCHAR(120) NOT NULL,
    email     VARCHAR(160) NOT NULL,
    turma     VARCHAR(40),
    telefone  VARCHAR(20),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- e-mail único sem diferenciar maiúsculas de minúsculas
CREATE UNIQUE INDEX uk_leitor_email ON leitor (lower(email));

CREATE TABLE livro (
    id             BIGSERIAL PRIMARY KEY,
    titulo         VARCHAR(200) NOT NULL,
    autor          VARCHAR(150) NOT NULL,
    ano_publicacao INTEGER CHECK (ano_publicacao BETWEEN 1000 AND 2100),
    isbn           VARCHAR(20)
);
CREATE UNIQUE INDEX uk_livro_isbn ON livro (isbn) WHERE isbn IS NOT NULL;

-- Um livro (obra) pode ter várias cópias físicas (exemplares), cada uma com seu número de tombo.
CREATE TABLE exemplar (
    id       BIGSERIAL PRIMARY KEY,
    livro_id BIGINT NOT NULL REFERENCES livro (id),
    tombo    VARCHAR(30) NOT NULL,
    CONSTRAINT uk_exemplar_tombo UNIQUE (tombo)
);
CREATE INDEX ix_exemplar_livro ON exemplar (livro_id);

CREATE TABLE emprestimo (
    id              BIGSERIAL PRIMARY KEY,
    leitor_id       BIGINT   NOT NULL REFERENCES leitor (id),
    exemplar_id     BIGINT   NOT NULL REFERENCES exemplar (id),
    data_emprestimo DATE     NOT NULL,
    data_prevista   DATE     NOT NULL,
    data_devolucao  DATE,
    renovacoes      INTEGER  NOT NULL DEFAULT 0 CHECK (renovacoes BETWEEN 0 AND 1),
    multa_centavos  INTEGER  NOT NULL DEFAULT 0 CHECK (multa_centavos >= 0),
    multa_paga      BOOLEAN  NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_prevista_apos_emprestimo CHECK (data_prevista >= data_emprestimo),
    CONSTRAINT ck_devolucao_apos_emprestimo CHECK (data_devolucao IS NULL OR data_devolucao >= data_emprestimo)
);

-- Garantia no banco (vale mesmo com requisições simultâneas): um exemplar só pode ter UM empréstimo ativo.
CREATE UNIQUE INDEX uk_emprestimo_exemplar_ativo ON emprestimo (exemplar_id) WHERE data_devolucao IS NULL;

CREATE INDEX ix_emprestimo_leitor ON emprestimo (leitor_id);
CREATE INDEX ix_emprestimo_ativos_prazo ON emprestimo (data_prevista) WHERE data_devolucao IS NULL;
