package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.DimensaoAtendidos;
import adra.ifsp.edu.br.api.domain.service.RoboConsultaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADRAbot no banco do time (stage). SÓ LEITURA: EXPLAIN ANALYZE executa o
 * SELECT, mas não grava nada. Mede o pior caso (frequência de 12 meses) e o
 * tempo de cada consulta do robô sem cache.
 *
 *   ./gradlew test -PstageTests --tests "*RoboDesempenhoStageIT"
 *
 * Os planos e tempos saem no log do teste. O limite de 3 s por consulta é a
 * régua: acima disso, rever índices ou baixar o período máximo.
 */
@SpringBootTest
@ActiveProfiles("stage")
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.cache.type=none"})
@Tag("stage")
class RoboDesempenhoStageIT {

    /** Acima disto a consulta incomoda quem está usando o sistema ao mesmo tempo. */
    private static final long LIMITE_MS = 3_000;

    @Autowired private JdbcClient jdbc;
    @Autowired private RoboConsultaService robo;

    @Test
    @DisplayName("EXPLAIN ANALYZE da frequência de 12 meses (a consulta mais pesada do robô)")
    void planoDaFrequenciaDe12Meses() {
        YearMonth ate = YearMonth.now();
        YearMonth de = ate.minusMonths(11);

        List<String> plano = jdbc.sql("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) "
                        + RoboConsultaRepository.SQL_FREQUENCIA_POR_TURMA_E_MES)
                .param("inicio", de.atDay(1))
                .param("fim", ate.atEndOfMonth())
                .param("oficineiroId", null)
                .query(String.class)
                .list();

        System.out.println("\n===== ADRAbot: EXPLAIN frequência " + de + " a " + ate + " =====");
        plano.forEach(System.out::println);

        String execucao = plano.stream().filter(l -> l.startsWith("Execution Time")).findFirst().orElse("");
        double ms = Double.parseDouble(execucao.replaceAll("[^0-9.]", ""));
        assertThat(ms).as("Tempo de execução da frequência de 12 meses (ms)").isLessThan(LIMITE_MS);
    }

    @Test
    @DisplayName("Tempo de cada consulta do robô sem cache")
    void tempoDeCadaConsulta() {
        YearMonth mes = YearMonth.now();
        Map<String, Supplier<?>> consultas = new LinkedHashMap<>();
        consultas.put("contexto", () -> robo.contexto(null));
        consultas.put("frequência 1 mês", () -> robo.frequencia(mes, mes, null));
        consultas.put("frequência 12 meses", () -> robo.frequencia(mes.minusMonths(11), mes, null));
        consultas.put("dia da semana 3 meses", () -> robo.frequenciaPorDiaDaSemana(mes.minusMonths(2), mes, null, null));
        consultas.put("faltas seguidas", () -> robo.faltasSeguidas(null, null));
        for (DimensaoAtendidos d : DimensaoAtendidos.values()) {
            consultas.put("distribuição " + d, () -> robo.distribuicao(d));
        }
        consultas.put("movimento do ano", () -> robo.movimento(LocalDate.now().getYear()));
        consultas.put("disciplinar do mês", () -> robo.disciplinar(mes));
        consultas.put("perfil social", robo::social);
        consultas.put("busca por nome", () -> robo.buscarAtendidos("ana", null));
        consultas.put("aniversariantes", () -> robo.aniversariantes(mes.getMonthValue(), null));
        consultas.put("um passo", robo::umPassoDaProximaMedida);

        System.out.println("\n===== ADRAbot: tempo por consulta (stage, sem cache) =====");
        Map<String, Long> lentas = new LinkedHashMap<>();
        for (var c : consultas.entrySet()) {
            long inicio = System.nanoTime();
            c.getValue().get();
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            System.out.printf("%-28s %6d ms%n", c.getKey(), ms);
            if (ms > LIMITE_MS) lentas.put(c.getKey(), ms);
        }
        assertThat(lentas).as("Consultas acima de %d ms", LIMITE_MS).isEmpty();
    }
}
