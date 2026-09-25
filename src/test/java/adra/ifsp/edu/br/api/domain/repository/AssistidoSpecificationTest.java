package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistidoSpecificationTest {

    @Test
    @DisplayName("CA-A03.1: removerAcentos deve normalizar caracteres diacríticos e caixa alta")
    void deveRemoverAcentosECaixaAlta() {
        assertEquals("joao", AssistidoSpecification.removerAcentos("João"));
        assertEquals("joao", AssistidoSpecification.removerAcentos("JOÃO"));
        assertEquals("joao andre", AssistidoSpecification.removerAcentos("João André"));
        assertEquals("erica alvares", AssistidoSpecification.removerAcentos("Érica Álvares"));
        assertEquals("conceicao", AssistidoSpecification.removerAcentos("Conceição"));
        assertEquals("acucar", AssistidoSpecification.removerAcentos("AÇÚCAR"));
        assertEquals("", AssistidoSpecification.removerAcentos(null));
        assertEquals("", AssistidoSpecification.removerAcentos("   "));
    }

    @Test
    @DisplayName("CA-70.4: assistido ATIVO é sempre elegível pra chamada, mesmo com data_saida antiga (reativação)")
    void ativoSempreElegivel() {
        LocalDate dataAula = LocalDate.of(2026, 9, 20);
        Assistido assistido = assistidoComStatus(StatusGeral.ATIVO, LocalDate.of(2026, 1, 1));

        assertTrue(AssistidoSpecification.elegivelParaChamada(assistido, dataAula));
    }

    @Test
    @DisplayName("CA-70.1: desligado com data_saida ANTES da data da aula não é elegível")
    void desligadoComSaidaAntesDaAulaNaoElegivel() {
        LocalDate dataAula = LocalDate.of(2026, 9, 20);
        Assistido assistido = assistidoComStatus(StatusGeral.INATIVO, LocalDate.of(2026, 9, 10));

        assertFalse(AssistidoSpecification.elegivelParaChamada(assistido, dataAula));
    }

    @Test
    @DisplayName("Caso de borda (Tasks do CA-70): aula no MESMO DIA da data_saida não é elegível — decisão do CA-70.1 (data_saida <= data_aula não aparece)")
    void desligadoComSaidaNoMesmoDiaDaAulaNaoElegivel() {
        LocalDate dataSaida = LocalDate.of(2026, 9, 20);

        Assistido assistido = assistidoComStatus(StatusGeral.INATIVO, dataSaida);

        assertFalse(AssistidoSpecification.elegivelParaChamada(assistido, dataSaida));
    }

    @Test
    @DisplayName("CA-70.2: desligado com data_saida DEPOIS da data da aula continua elegível (histórico preservado)")
    void desligadoComSaidaDepoisDaAulaContinuaElegivel() {
        LocalDate dataAula = LocalDate.of(2026, 9, 20);
        Assistido assistido = assistidoComStatus(StatusGeral.INATIVO, LocalDate.of(2026, 9, 21));

        assertTrue(AssistidoSpecification.elegivelParaChamada(assistido, dataAula));
    }

    @Test
    @DisplayName("Desligado sem data_saida preenchida (dado inconsistente) não é elegível, por segurança")
    void desligadoSemDataSaidaNaoElegivel() {
        Assistido assistido = assistidoComStatus(StatusGeral.INATIVO, null);

        assertFalse(AssistidoSpecification.elegivelParaChamada(assistido, LocalDate.of(2026, 9, 20)));
    }

    private static Assistido assistidoComStatus(StatusGeral status, LocalDate dataSaida) {
        Assistido assistido = new Assistido();
        assistido.setStatus(status);
        assistido.setDataSaida(dataSaida);
        return assistido;
    }
}
