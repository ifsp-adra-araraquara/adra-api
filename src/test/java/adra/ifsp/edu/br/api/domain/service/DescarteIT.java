package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.OrfaosDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.PreviaDescarteDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos.ObjetoArmazenado;

/**
 * Prazo de guarda (PR 5). Sem @Transactional de propósito: a remoção no Storage
 * acontece depois do commit. Os dados "antigos" são recuados 6 anos pelo banco;
 * os dos outros testes são de hoje e ficam fora do descarte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
class DescarteIT {

    private final int ano = LocalDate.now().getYear();
    private final int anoAntigo = ano - 6;
    private final OffsetDateTime seisAnosAtras = OffsetDateTime.now().minusYears(6);

    @Autowired private MockMvc mvc;
    @Autowired private AssistidoService assistidoService;
    @Autowired private DescarteService descarteService;
    @Autowired private ArquivosOrfaosService orfaosService;
    @Autowired private RemocaoNoArmazenamento remocao;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    @BeforeEach
    void preparar() {
        for (String tabela : List.of("assistido", "responsavel", "usuario")) {
            jdbc.execute("alter table adra." + tabela + " alter column criado_em set default now()");
            jdbc.execute("alter table adra." + tabela + " alter column atualizado_em set default now()");
        }
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        jdbc.execute("alter table adra.arquivo alter column criado_em set default now()");
        jdbc.execute("alter table adra.pre_cadastro alter column criado_em set default now()");
        jdbc.execute("alter table adra.pre_cadastro alter column dados set default '{}'::jsonb");
        reset(storage);
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
    }

    @Test
    @DisplayName("Descarte: sai o ano vencido; a ficha só sai sem matrícula no prazo; o responsável fica com o outro filho")
    void descarteDoAnoVencido() {
        Turma grupo = grupo();
        Long mae = criarResponsavel("Mãe Descarte");
        Long saiInteiro = cadastrar("Ana Descarte", grupo, mae);
        Long continua = cadastrar("Bruno Descarte", grupo, mae);

        // Ana: só a matrícula antiga (encerrada). Bruno: a antiga e a deste ano.
        Long matriculaAntigaAna = envelhecer(matricula(saiInteiro, ano));
        Long matriculaAntigaBruno = inserirMatriculaAntiga(continua);
        Long matriculaAtualBruno = matricula(continua, ano);

        String docAna = arquivo("ASSISTIDO", saiInteiro, "DOCUMENTO_IDENTIDADE", matriculaAntigaAna);
        String fotoAna = arquivo("ASSISTIDO", saiInteiro, "FOTO_PERFIL", null);
        String docBrunoAntigo = arquivo("ASSISTIDO", continua, "DOCUMENTO_IDENTIDADE", matriculaAntigaBruno);
        String docBrunoAtual = arquivo("ASSISTIDO", continua, "DOCUMENTO_IDENTIDADE", matriculaAtualBruno);
        String docMaeDaAna = arquivo("RESPONSAVEL", mae, "DOCUMENTO_IDENTIDADE", matriculaAntigaAna);
        String docMaeDoBruno = arquivo("RESPONSAVEL", mae, "DOCUMENTO_IDENTIDADE", matriculaAtualBruno);

        Long preCadastroReprovado = preCadastroAntigo("DESCARTADO");
        String anexoReprovado = arquivo("PRE_CADASTRO", preCadastroReprovado, "DECLARACAO_RESIDENCIA", null);

        Usuario coordenador = coordenador();
        Long faltaAntiga = falta(grupo, coordenador, LocalDate.of(anoAntigo, 3, 10), continua);
        Long faltaAtual = falta(grupo, coordenador, LocalDate.of(ano, 1, 2), continua);

        // Disciplinar: documento assinado aponta para o arquivo; advertência aponta para a ocorrência de origem.
        String assinadoAna = arquivo("ASSISTIDO", saiInteiro, "DOCUMENTO_DISCIPLINAR_ASSINADO", null);
        Long ocorrenciaAna = registro(saiInteiro, LocalDate.of(anoAntigo, 4, 1), coordenador, assinadoAna, null);
        registro(saiInteiro, LocalDate.of(anoAntigo, 5, 1), coordenador, null, ocorrenciaAna);
        String assinadoBrunoAntigo = arquivo("ASSISTIDO", continua, "DOCUMENTO_DISCIPLINAR_ASSINADO", null);
        Long registroBrunoAntigo = registro(continua, LocalDate.of(anoAntigo, 4, 1), coordenador, assinadoBrunoAntigo, null);
        Long registroBrunoAtual = registro(continua, LocalDate.of(ano, 1, 3), coordenador, null, registroBrunoAntigo);
        jdbc.update("""
                insert into adra.log_auditoria (modulo, entidade_afetada, entidade_id, acao, valor_novo)
                values ('ASSISTIDOS', 'assistido', ?, 'CRIAR', '{"nomeCompleto":"Ana Descarte"}'::jsonb)""", saiInteiro);

        PreviaDescarteDTO previa = descarteService.previa();
        assertThat(previa.quemSaiPorInteiro()).extracting(PreviaDescarteDTO.AtendidoRemovidoDTO::assistidoId)
                .contains(saiInteiro).doesNotContain(continua);
        assertThat(previa.anos()).contains(anoAntigo);

        descarteService.descartar();

        // Ana sai por inteiro, com foto, documento e o histórico.
        assertThat(existe("assistido", "assistido_id", saiInteiro)).isFalse();
        assertThat(existe("matricula", "assistido_id", saiInteiro)).isFalse();
        // Bruno fica, sem o ano vencido.
        assertThat(existe("assistido", "assistido_id", continua)).isTrue();
        assertThat(existe("matricula", "matricula_id", matriculaAntigaBruno)).isFalse();
        assertThat(existe("matricula", "matricula_id", matriculaAtualBruno)).isTrue();
        assertThat(existe("presenca", "presenca_id", faltaAntiga)).isFalse();
        assertThat(existe("presenca", "presenca_id", faltaAtual)).isTrue();
        // A mãe continua (ainda tem o Bruno); só a cópia do documento enviada com a Ana sai.
        assertThat(existe("responsavel", "responsavel_id", mae)).isTrue();
        // Pré-cadastro reprovado há mais de 5 anos sai com o anexo.
        assertThat(existe("pre_cadastro", "pre_cadastro_id", preCadastroReprovado)).isFalse();

        // Disciplinar do ano vencido sai com o documento; o deste ano fica, só sem a referência à origem que saiu.
        assertThat(existe("registro_disciplinar", "assistido_id", saiInteiro)).isFalse();
        assertThat(existe("registro_disciplinar", "registro_id", registroBrunoAntigo)).isFalse();
        assertThat(existe("registro_disciplinar", "registro_id", registroBrunoAtual)).isTrue();

        List<String> saem = List.of(docAna, fotoAna, docBrunoAntigo, docMaeDaAna, anexoReprovado, assinadoAna,
                assinadoBrunoAntigo);
        assertThat(jdbc.queryForList("select chave from adra.arquivo", String.class))
                .doesNotContainAnyElementsOf(saem)
                .contains(docBrunoAtual, docMaeDoBruno);
        saem.forEach(chave -> verify(storage).remover(chave));
        verify(storage, never()).remover(docBrunoAtual);

        // Auditoria fica, sem os dados pessoais.
        assertThat(jdbc.queryForObject("""
                select count(*) from adra.log_auditoria
                 where entidade_afetada = 'assistido' and entidade_id = ? and valor_novo is not null""",
                Integer.class, saiInteiro)).isZero();
        assertThat(descarteService.historico()).isNotEmpty();
    }

    @Test
    @DisplayName("Matrícula ativa nunca é descartada, mesmo antiga")
    void matriculaAtivaFica() {
        Long atendido = cadastrar("Caio Ativo Antigo", grupo(), criarResponsavel("Mãe Caio"));
        Long matricula = matricula(atendido, ano);
        jdbc.update("update adra.matricula set criado_em = ? where matricula_id = ?", seisAnosAtras, matricula);

        assertThat(descarteService.previa().quemSaiPorInteiro())
                .extracting(PreviaDescarteDTO.AtendidoRemovidoDTO::assistidoId).doesNotContain(atendido);
    }

    @Test
    @DisplayName("Storage fora do ar: a chave vai para a fila e sai na próxima tentativa")
    void filaDeRemocao() {
        String chave = "assistido/documento_identidade/" + UUID.randomUUID() + ".jpg";
        doThrow(new RuntimeException("Storage fora")).when(storage).remover(chave);

        remocao.removerAposCommit(List.of(new RemocaoNoArmazenamento.Chave(ProvedorArmazenamento.SUPABASE, chave)));
        assertThat(existe("arquivo_remocao_pendente", "chave", chave)).isTrue();

        reset(storage);
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        remocao.processarFila();
        verify(storage).remover(chave);
        assertThat(existe("arquivo_remocao_pendente", "chave", chave)).isFalse();
    }

    @Test
    @DisplayName("Órfãos: só o que não tem dono e tem mais de 24 h")
    void arquivosOrfaos() {
        String comDono = arquivo("ASSISTIDO", 999_999L, "FOTO_PERFIL", null);
        String orfaoAntigo = "assistido/foto_perfil/" + UUID.randomUUID() + ".jpg";
        String orfaoNovo = "assistido/foto_perfil/" + UUID.randomUUID() + ".jpg";
        Instant ontem = Instant.now().minus(Duration.ofDays(2));
        when(storage.listarTodos()).thenReturn(List.of(
                new ObjetoArmazenado(comDono, ontem, 10),
                new ObjetoArmazenado(orfaoAntigo, ontem, 2048),
                new ObjetoArmazenado(orfaoNovo, Instant.now(), 10)));

        OrfaosDTO antes = orfaosService.verificar();
        assertThat(antes.arquivos()).isEqualTo(1);
        assertThat(antes.bytes()).isEqualTo(2048);

        orfaosService.remover();
        verify(storage).remover(orfaoAntigo);
        verify(storage, never()).remover(comDono);
        verify(storage, never()).remover(orfaoNovo);
    }

    @Test
    @DisplayName("Só o Coordenador vê e executa o descarte")
    void permissao() throws Exception {
        mvc.perform(get("/api/guarda-dados/previa").with(perfil(ASSISTENTE_SOCIAL))).andExpect(status().isForbidden());
        mvc.perform(post("/api/guarda-dados/descartar").with(perfil(ASSISTENTE_SOCIAL))).andExpect(status().isForbidden());
        mvc.perform(get("/api/guarda-dados/previa").with(perfil(COORDENADOR))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------

    private Long cadastrar(String nome, Turma grupo, Long responsavelId) {
        VinculoFamiliarComResponsavelRequestDTO mae = new VinculoFamiliarComResponsavelRequestDTO(
                "Mãe", null, null, null, null, null, null, null, null, null, null, "Mãe", true, false, false, responsavelId);
        return assistidoService.cadastrar(new AssistidoRequestDTO(nome, LocalDate.of(2016, 5, 10), null, null, null, null,
                grupo.getTurmaId(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                true, false, false, false, true, List.of(mae), null)).assistidoId();
    }

    private Long criarResponsavel(String nome) {
        return jdbc.queryForObject("insert into adra.responsavel (nome_completo) values (?) returning responsavel_id",
                Long.class, nome);
    }

    private Long matricula(Long assistidoId, int anoDaMatricula) {
        return jdbc.queryForObject("select matricula_id from adra.matricula where assistido_id = ? and ano = ?",
                Long.class, assistidoId, anoDaMatricula);
    }

    /** A matrícula de hoje vira uma de 6 anos atrás, já encerrada. */
    private Long envelhecer(Long matriculaId) {
        jdbc.update("update adra.matricula set ano = ?, status = 'ENCERRADA', criado_em = ? where matricula_id = ?",
                anoAntigo, seisAnosAtras, matriculaId);
        return matriculaId;
    }

    private Long inserirMatriculaAntiga(Long assistidoId) {
        return jdbc.queryForObject("""
                insert into adra.matricula (assistido_id, ano, status, aceite_lgpd, autoriza_uso_imagem,
                    autoriza_eventos_passeios, pode_ir_embora_sozinho, ciente_frequencia_obrigatoria, criado_em, atualizado_em)
                values (?, ?, 'ENCERRADA', true, false, false, false, true, ?, ?) returning matricula_id""",
                Long.class, assistidoId, anoAntigo, seisAnosAtras, seisAnosAtras);
    }

    private String arquivo(String entidade, Long id, String categoria, Long matriculaId) {
        String chave = entidade.toLowerCase() + "/" + categoria.toLowerCase() + "/" + UUID.randomUUID() + ".jpg";
        jdbc.update("""
                insert into adra.arquivo (entidade_tipo, entidade_id, categoria, provedor, chave, mime_type, tamanho_bytes,
                                          hash_sha256, matricula_id)
                values (?, ?, ?, 'SUPABASE', ?, 'image/jpeg', 100, ?, ?)""",
                entidade, id, categoria, chave, "a".repeat(64), matriculaId);
        return chave;
    }

    private Long preCadastroAntigo(String status) {
        return jdbc.queryForObject("""
                insert into adra.pre_cadastro (nome_completo, responsavel_nome, status, ano_referencia, criado_em,
                                               visto_assistente_social)
                values ('Envio Antigo', 'Mãe', ?, ?, ?, false) returning pre_cadastro_id""",
                Long.class, status, anoAntigo, seisAnosAtras);
    }

    private Long falta(Turma grupo, Usuario usuario, LocalDate dia, Long assistidoId) {
        Long chamada = jdbc.queryForObject("""
                insert into adra.chamada (turma_id, data_chamada, criado_por_id, atualizado_por_id, criado_em, atualizado_em)
                values (?, ?, ?, ?, now(), now()) returning chamada_id""",
                Long.class, grupo.getTurmaId(), dia, usuario.getUsuarioId(), usuario.getUsuarioId());
        return jdbc.queryForObject("""
                insert into adra.presenca (chamada_id, assistido_id, status_presenca, criado_por_id, atualizado_por_id,
                                           criado_em, atualizado_em)
                values (?, ?, 'FALTA', ?, ?, now(), now()) returning presenca_id""",
                Long.class, chamada, assistidoId, usuario.getUsuarioId(), usuario.getUsuarioId());
    }

    private Long registro(Long assistidoId, LocalDate dia, Usuario usuario, String chaveDocumento, Long origem) {
        Long arquivoId = chaveDocumento == null ? null
                : jdbc.queryForObject("select arquivo_id from adra.arquivo where chave = ?", Long.class, chaveDocumento);
        return jdbc.queryForObject("""
                insert into adra.registro_disciplinar (assistido_id, nivel, origem, sequencia, data_registro, descricao,
                    arquivado, registrado_por_id, criado_em, documento_arquivo_id, registro_origem_id)
                values (?, 'OCORRENCIA', 'MANUAL', 1, ?, 'Registro de teste', false, ?, now(), ?, ?)
                returning registro_id""",
                Long.class, assistidoId, dia, usuario.getUsuarioId(), arquivoId, origem);
    }

    private Usuario coordenador() {
        NivelPermissao nivel = nivelPermissaoRepository.findByNome(NomeNivelPermissao.COORDENADOR)
                .orElseGet(() -> nivelPermissaoRepository.save(NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build()));
        return usuarioRepository.save(Usuario.builder().nivelPermissao(nivel).nomeCompleto("Coordenação Descarte")
                .email("descarte-" + UUID.randomUUID() + "@teste.local").build());
    }

    private Turma grupo() {
        Turma turma = new Turma();
        turma.setNomeTurma("Grupo Descarte " + UUID.randomUUID().toString().substring(0, 6));
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma.setDiasDaSemana(new ArrayList<>());
        return turmaRepository.save(turma);
    }

    private boolean existe(String tabela, String coluna, Object valor) {
        return jdbc.queryForObject("select count(*) from adra." + tabela + " where " + coluna + " = ?", Integer.class, valor) > 0;
    }

    private static RequestPostProcessor perfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
