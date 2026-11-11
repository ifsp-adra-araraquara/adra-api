package adra.ifsp.edu.br.api.domain.service;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.model.ArquivoRemocaoPendente;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRemocaoPendenteRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;

/**
 * Remove binários do Storage sem deixar órfão: só depois do commit (se o banco
 * desfizer, o arquivo continua lá e continua referenciado) e, se o Storage
 * falhar, a chave vai para a fila {@code arquivo_remocao_pendente}, que é
 * reprocessada todo dia de madrugada.
 */
@Service
public class RemocaoNoArmazenamento {

    private static final Logger log = LoggerFactory.getLogger(RemocaoNoArmazenamento.class);

    public record Chave(ProvedorArmazenamento provedor, String chave) {
    }

    private final List<ArmazenamentoArquivos> armazenamentos;
    private final ArquivoRemocaoPendenteRepository pendentes;
    private final TransactionTemplate novaTransacao;

    public RemocaoNoArmazenamento(List<ArmazenamentoArquivos> armazenamentos, ArquivoRemocaoPendenteRepository pendentes,
                                  PlatformTransactionManager transacoes) {
        this.armazenamentos = armazenamentos;
        this.pendentes = pendentes;
        this.novaTransacao = new TransactionTemplate(transacoes);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Agenda a remoção para depois do commit da transação atual (ou faz já, se não houver transação). */
    public void removerAposCommit(Collection<Chave> chaves) {
        if (chaves.isEmpty()) {
            return;
        }
        List<Chave> copia = List.copyOf(chaves);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            copia.forEach(this::removerOuEnfileirar);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                copia.forEach(RemocaoNoArmazenamento.this::removerOuEnfileirar);
            }
        });
    }

    void removerOuEnfileirar(Chave chave) {
        try {
            armazenamento(chave.provedor()).remover(chave.chave());
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel remover {} do storage {}; fica na fila de remocao", chave.chave(), chave.provedor(), e);
            novaTransacao.executeWithoutResult(status -> {
                ArquivoRemocaoPendente pendente = pendentes.findById(chave.chave())
                        .orElseGet(() -> new ArquivoRemocaoPendente(chave.chave(), chave.provedor()));
                pendente.falhou(e.getMessage());
                pendentes.save(pendente);
            });
        }
    }

    /** Tenta de novo o que ficou na fila. Todo dia às 3h30 e quando o Coordenador pede a limpeza. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public int processarFila() {
        int removidos = 0;
        for (ArquivoRemocaoPendente pendente : pendentes.findTop200ByOrderByCriadoEmAsc()) {
            try {
                armazenamento(pendente.getProvedor()).remover(pendente.getChave());
                novaTransacao.executeWithoutResult(status -> pendentes.deleteById(pendente.getChave()));
                removidos++;
            } catch (RuntimeException e) {
                novaTransacao.executeWithoutResult(status -> pendentes.findById(pendente.getChave()).ifPresent(p -> {
                    p.falhou(e.getMessage());
                    pendentes.save(p);
                }));
            }
        }
        return removidos;
    }

    public long totalNaFila() {
        return pendentes.count();
    }

    private ArmazenamentoArquivos armazenamento(ProvedorArmazenamento provedor) {
        return armazenamentos.stream()
                .filter(a -> a.provedor() == provedor)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Armazenamento não configurado: " + provedor));
    }
}
