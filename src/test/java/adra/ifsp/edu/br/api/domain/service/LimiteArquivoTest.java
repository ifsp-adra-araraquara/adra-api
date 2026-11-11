package adra.ifsp.edu.br.api.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.exception.ArquivoMuitoGrandeException;
import adra.ifsp.edu.br.api.exception.GlobalExceptionHandler;

class LimiteArquivoTest {

    private final ProcessadorArquivo processador = new ProcessadorArquivo();

    private static byte[] pdfDe(long bytes) {
        byte[] pdf = new byte[(int) bytes];
        Arrays.fill(pdf, (byte) ' ');
        byte[] cabecalho = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(cabecalho, 0, pdf, 0, cabecalho.length);
        return pdf;
    }

    @ParameterizedTest
    @EnumSource(CategoriaArquivo.class)
    @DisplayName("Nenhuma categoria aceita mais que o limite do bucket do Supabase")
    void categoriaNaoPassaDoBucket(CategoriaArquivo categoria) {
        assertThat(categoria.getTamanhoMaximoBytes()).isLessThanOrEqualTo(CategoriaArquivo.Limite.BUCKET);
    }

    @ParameterizedTest
    @EnumSource(value = CategoriaArquivo.class, names = {"DECLARACAO_RESIDENCIA", "DOCUMENTO_DISCIPLINAR_ASSINADO"})
    @DisplayName("PDF acima de 2 MB é recusado na API com mensagem clara, antes de ir ao storage")
    void pdfGrandeRecusadoComMensagem(CategoriaArquivo categoria) {
        long meioMegaAcima = CategoriaArquivo.Limite.BUCKET + CategoriaArquivo.Limite.BUCKET / 4;

        assertThatThrownBy(() -> processador.processar(pdfDe(meioMegaAcima), categoria))
                .isInstanceOf(ArquivoMuitoGrandeException.class)
                .hasMessage("Arquivo muito grande (2,5 MB). O limite é 2 MB. Se possível, envie uma foto do documento.");
    }

    @Test
    @DisplayName("PDF no limite exato passa")
    void pdfNoLimitePassa() {
        var processado = processador.processar(pdfDe(CategoriaArquivo.Limite.BUCKET),
                CategoriaArquivo.DECLARACAO_RESIDENCIA);
        assertThat(processado.conteudo()).hasSize((int) CategoriaArquivo.Limite.BUCKET);
    }

    @Test
    @DisplayName("Arquivo grande vira 413 com a mensagem para o usuário")
    void handlerDevolve413() {
        var resposta = new GlobalExceptionHandler().tratarArquivoMuitoGrande(
                ArquivoMuitoGrandeException.acimaDe(CategoriaArquivo.Limite.BUCKET),
                new MockHttpServletRequest("POST", "/api/arquivos"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(resposta.getBody().mensagem()).isEqualTo("Arquivo muito grande. O limite é 2 MB por arquivo.");
    }
}
