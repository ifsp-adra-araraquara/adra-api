package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.exception.RoboOcupadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADRAbot: uma consulta por vez; quem espera demais recebe "ocupado", e a vaga sempre volta. */
class RoboLimitadorTest {

    @Test
    @DisplayName("Com a vaga ocupada, a segunda consulta desiste depois da espera máxima")
    void segundaConsultaEsperaEDesiste() throws Exception {
        RoboLimitador limitador = new RoboLimitador(1, Duration.ofMillis(100));
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService outra = Executors.newSingleThreadExecutor();
        try {
            Future<String> primeira = outra.submit(() -> limitador.executar(() -> {
                dentro.countDown();
                try {
                    liberar.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            }));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> limitador.executar(() -> "não roda")).isInstanceOf(RoboOcupadoException.class);

            liberar.countDown();
            assertThat(primeira.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(limitador.executar(() -> "depois")).isEqualTo("depois");
        } finally {
            outra.shutdownNow();
        }
    }

    @Test
    @DisplayName("Erro na consulta devolve a vaga")
    void erroDevolveAVaga() {
        RoboLimitador limitador = new RoboLimitador(1, Duration.ofMillis(50));

        assertThatThrownBy(() -> limitador.executar(() -> {
            throw new IllegalStateException("falhou");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limitador.vagasLivres()).isEqualTo(1);
        assertThat(limitador.executar(() -> 42)).isEqualTo(42);
    }
}
