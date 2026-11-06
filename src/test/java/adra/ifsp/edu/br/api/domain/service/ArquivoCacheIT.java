package adra.ifsp.edu.br.api.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.arquivo.ArquivoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.arquivo.UrlArquivoDTO;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;

@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.cache.type=caffeine",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
class ArquivoCacheIT {

    private static final long ASSISTIDO_ID = 4242L;

    @Autowired private ArquivoService arquivoService;
    @Autowired private ArquivoRepository arquivoRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArmazenamentoArquivos storage;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        when(storage.urlTemporaria(anyString(), any())).thenReturn(URI.create("https://storage.test/foto?token=1"));
    }

    @Test
    @DisplayName("Foto e URL assinada saem do cache até uma escrita em arquivos")
    void fotoEUrlEmCacheAteUmaEscrita() {
        Long fotoId = arquivoRepository.save(arquivo(CategoriaArquivo.FOTO_PERFIL, "foto.webp")).getArquivoId();
        Long documentoId = arquivoRepository.save(arquivo(CategoriaArquivo.DOCUMENTO_IDENTIDADE, "rg.webp")).getArquivoId();

        assertThat(fotosDoAssistido()).hasSize(1);
        UrlArquivoDTO url = arquivoService.gerarUrl(fotoId);
        assertThat(arquivoService.gerarUrl(fotoId)).isEqualTo(url);
        verify(storage, times(1)).urlTemporaria(anyString(), any());

        jdbcTemplate.update("update adra.arquivo set excluido_em = now() where arquivo_id = ?", fotoId);
        assertThat(fotosDoAssistido()).as("ainda vem do cache").hasSize(1);

        arquivoService.remover(documentoId);
        assertThat(fotosDoAssistido()).as("a escrita limpou o cache").isEmpty();
    }

    private List<ArquivoResponseDTO> fotosDoAssistido() {
        return arquivoService.listar(EntidadeArquivo.ASSISTIDO, ASSISTIDO_ID, CategoriaArquivo.FOTO_PERFIL);
    }

    private static Arquivo arquivo(CategoriaArquivo categoria, String nome) {
        return Arquivo.builder()
                .entidadeTipo(EntidadeArquivo.ASSISTIDO)
                .entidadeId(ASSISTIDO_ID)
                .categoria(categoria)
                .provedor(ProvedorArmazenamento.SUPABASE)
                .chave("assistido/" + ASSISTIDO_ID + "/" + nome)
                .mimeType("image/webp")
                .tamanhoBytes(10L)
                .hashSha256("a".repeat(64))
                .build();
    }
}
