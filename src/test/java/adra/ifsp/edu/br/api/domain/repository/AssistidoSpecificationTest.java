package adra.ifsp.edu.br.api.domain.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
