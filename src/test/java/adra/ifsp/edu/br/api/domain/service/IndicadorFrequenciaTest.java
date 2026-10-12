package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static adra.ifsp.edu.br.api.domain.enums.StatusPresenca.FALTA;
import static adra.ifsp.edu.br.api.domain.enums.StatusPresenca.FALTA_JUSTIFICADA;
import static adra.ifsp.edu.br.api.domain.enums.StatusPresenca.PRESENTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicadorFrequenciaTest {

    private static List<StatusPresenca> repetir(StatusPresenca status, int vezes) {
        return Collections.nCopies(vezes, status);
    }

    @Test
    @DisplayName("Sem aula realizada: sem percentual e fora de acompanhamento")
    void semAulas() {
        IndicadorFrequencia ind = IndicadorFrequencia.de(List.of(), StatusGeral.ATIVO);
        assertNull(ind.percentual());
        assertEquals(0, ind.totalAulas());
        assertFalse(ind.emAcompanhamento());
    }

    @Test
    @DisplayName("Percentual arredondado conta só PRESENTE (justificada não é presença)")
    void percentualSoPresentes() {
        // 2 presentes de 3 -> 67%
        IndicadorFrequencia ind = IndicadorFrequencia.de(List.of(PRESENTE, FALTA_JUSTIFICADA, PRESENTE), StatusGeral.ATIVO);
        assertEquals(67, ind.percentual());
        assertEquals(3, ind.totalAulas());
    }

    @Test
    @DisplayName("Exatamente 75% não entra em acompanhamento; abaixo entra")
    void limiteDaFrequenciaMinima() {
        List<StatusPresenca> setentaECinco = List.of(FALTA_JUSTIFICADA, PRESENTE, PRESENTE, PRESENTE);
        assertEquals(75, IndicadorFrequencia.de(setentaECinco, StatusGeral.ATIVO).percentual());
        assertFalse(IndicadorFrequencia.de(setentaECinco, StatusGeral.ATIVO).emAcompanhamento());

        List<StatusPresenca> setenta = new java.util.ArrayList<>(repetir(PRESENTE, 7));
        setenta.addAll(0, List.of(PRESENTE, FALTA_JUSTIFICADA, FALTA_JUSTIFICADA, FALTA_JUSTIFICADA)); // 8/11 = 73%
        assertTrue(IndicadorFrequencia.de(setenta, StatusGeral.ATIVO).emAcompanhamento());
    }

    @Test
    @DisplayName("3 faltas seguidas recentes entram em acompanhamento mesmo com frequência alta")
    void faltasConsecutivas() {
        List<StatusPresenca> lista = new java.util.ArrayList<>(List.of(FALTA, FALTA, FALTA));
        lista.addAll(repetir(PRESENTE, 20)); // 20/23 = 87%
        IndicadorFrequencia ind = IndicadorFrequencia.de(lista, StatusGeral.ATIVO);
        assertEquals(87, ind.percentual());
        assertEquals(3, ind.faltasConsecutivas());
        assertTrue(ind.emAcompanhamento());
    }

    @Test
    @DisplayName("Falta justificada interrompe a sequência de faltas")
    void justificadaInterrompeSequencia() {
        List<StatusPresenca> lista = new java.util.ArrayList<>(List.of(FALTA, FALTA, FALTA_JUSTIFICADA, FALTA));
        lista.addAll(repetir(PRESENTE, 30));
        IndicadorFrequencia ind = IndicadorFrequencia.de(lista, StatusGeral.ATIVO);
        assertEquals(2, ind.faltasConsecutivas());
        assertFalse(ind.emAcompanhamento()); // 30/34 = 88%
    }

    @Test
    @DisplayName("Inativo nunca fica em acompanhamento")
    void inativoNaoEntra() {
        assertFalse(IndicadorFrequencia.de(repetir(FALTA, 5), StatusGeral.INATIVO).emAcompanhamento());
    }
}
