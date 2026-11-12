package adra.ifsp.edu.br.api.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

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

    @Test
    @DisplayName("Várias imagens ao mesmo tempo: processa na vez de cada uma e libera as vagas")
    void imagensSimultaneas() throws Exception {
        byte[] png = pngDe(1200, 900);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<ProcessadorArquivo.ArquivoProcessado>> envios = IntStream.range(0, 6)
                    .mapToObj(i -> pool.submit(() -> processador.processar(png, CategoriaArquivo.DOCUMENTO_IDENTIDADE)))
                    .toList();
            for (Future<ProcessadorArquivo.ArquivoProcessado> envio : envios) {
                assertThat(envio.get().mimeType()).isEqualTo("image/jpeg");
            }
        } finally {
            pool.shutdown();
        }
        // As vagas voltaram: mais uma passa direto.
        assertThat(processador.processar(png, CategoriaArquivo.DOCUMENTO_IDENTIDADE).extensao()).isEqualTo("jpg");
    }

    private static byte[] pngDe(int largura, int altura) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), "png", saida);
        return saida.toByteArray();
    }
}
