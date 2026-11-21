package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.exception.RoboOcupadoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * ADRAbot: no máximo {@code adra.robo.consultas-simultaneas} consultas do robô
 * rodando ao mesmo tempo na API inteira (padrão 1). O pool de conexões é
 * pequeno (3) e a chamada do dia não pode esperar por um relatório: o robô
 * usa no máximo uma conexão por vez e o resto do pool fica livre.
 *
 * <p>Fila justa; quem esperar mais que {@code adra.robo.espera-maxima}
 * recebe 503 ("tente de novo em instantes") em vez de prender a requisição.
 * Acerto de cache não passa por aqui (o @Cacheable responde antes).
 */
@Component
public class RoboLimitador {

    private final Semaphore vagas;
    private final Duration esperaMaxima;

    public RoboLimitador(@Value("${adra.robo.consultas-simultaneas:1}") int simultaneas,
                         @Value("${adra.robo.espera-maxima:10s}") Duration esperaMaxima) {
        this.vagas = new Semaphore(Math.max(1, simultaneas), true);
        this.esperaMaxima = esperaMaxima;
    }

    public <T> T executar(Supplier<T> consulta) {
        boolean entrou;
        try {
            entrou = vagas.tryAcquire(esperaMaxima.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoboOcupadoException("A consulta foi interrompida. Tente de novo.");
        }
        if (!entrou) {
            throw new RoboOcupadoException("O ADRAbot está respondendo outra pergunta. Tente de novo em instantes.");
        }
        try {
            return consulta.get();
        } finally {
            vagas.release();
        }
    }

    /** Vagas livres agora (para teste e diagnóstico). */
    int vagasLivres() {
        return vagas.availablePermits();
    }
}
