package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicadorFrequenciaTest {

    @Test
    @DisplayName("Sem aula realizada: sem percentual e fora de acompanhamento")
    void semAulas() {
        IndicadorFrequencia ind = IndicadorFrequencia.de(0, 0, 0, StatusGeral.ATIVO);
        assertNull(ind.percentual());
        assertEquals(0, ind.totalAulas());
        assertFalse(ind.emAcompanhamento());
    }

    @Test
    @DisplayName("Percentual arredondado")
    void percentualArredondado() {
        IndicadorFrequencia ind = IndicadorFrequencia.de(3, 2, 0, StatusGeral.ATIVO);
        assertEquals(67, ind.percentual());
        assertEquals(3, ind.totalAulas());
    }

    @Test
    @DisplayName("Exatamente 75% não entra em acompanhamento; abaixo entra")
    void limiteDaFrequenciaMinima() {
        IndicadorFrequencia setentaECinco = IndicadorFrequencia.de(4, 3, 0, StatusGeral.ATIVO);
        assertEquals(75, setentaECinco.percentual());
        assertFalse(setentaECinco.emAcompanhamento());

        assertTrue(IndicadorFrequencia.de(11, 8, 0, StatusGeral.ATIVO).emAcompanhamento());
    }

    @Test
    @DisplayName("3 faltas seguidas recentes entram em acompanhamento mesmo com frequência alta")
    void faltasConsecutivas() {
        IndicadorFrequencia ind = IndicadorFrequencia.de(23, 20, 3, StatusGeral.ATIVO);
        assertEquals(87, ind.percentual());
        assertEquals(3, ind.faltasConsecutivas());
        assertTrue(ind.emAcompanhamento());
    }

    @Test
    @DisplayName("2 faltas seguidas com frequência alta não entram em acompanhamento")
    void duasFaltasConsecutivas() {
        assertFalse(IndicadorFrequencia.de(34, 30, 2, StatusGeral.ATIVO).emAcompanhamento());
    }

    @Test
    @DisplayName("Inativo nunca fica em acompanhamento")
    void inativoNaoEntra() {
        assertFalse(IndicadorFrequencia.de(5, 0, 5, StatusGeral.INATIVO).emAcompanhamento());
    }
}
