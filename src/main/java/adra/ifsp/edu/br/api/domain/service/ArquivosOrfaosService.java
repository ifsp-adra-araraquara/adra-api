package adra.ifsp.edu.br.api.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import adra.ifsp.edu.br.api.domain.dto.guarda.OrfaosDTO;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos.ObjetoArmazenado;
import lombok.RequiredArgsConstructor;

/**
 * Rede de segurança do Storage: objeto no bucket sem linha ativa em adra.arquivo
 * (upload que falhou no meio, remoção que não aconteceu antes da fila existir).
 * Só conta o que tem mais de 24 h, para não pegar um envio em andamento.
 * Não é agendado: o Coordenador verifica e remove pela tela.
 */
@Service
@RequiredArgsConstructor
public class ArquivosOrfaosService {

    static final Duration CARENCIA = Duration.ofHours(24);

    private final List<ArmazenamentoArquivos> armazenamentos;
    private final JdbcTemplate jdbc;
    private final RemocaoNoArmazenamento remocao;

    public OrfaosDTO verificar() {
        List<Orfao> orfaos = orfaos();
        return new OrfaosDTO(orfaos.size(), orfaos.stream().mapToLong(o -> o.objeto().tamanhoBytes()).sum(),
                remocao.totalNaFila());
    }

    /** Tenta de novo a fila e remove os órfãos encontrados; devolve o que sobrou. */
    public OrfaosDTO remover() {
        remocao.processarFila();
        for (Orfao orfao : orfaos()) {
            remocao.removerOuEnfileirar(new RemocaoNoArmazenamento.Chave(orfao.provedor(), orfao.objeto().chave()));
        }
        return verificar();
    }

    private record Orfao(ProvedorArmazenamento provedor, ObjetoArmazenado objeto) {
    }

    private List<Orfao> orfaos() {
        Set<String> referenciadas = new HashSet<>(
                jdbc.queryForList("select chave from adra.arquivo where excluido_em is null", String.class));
        Instant antesDe = Instant.now().minus(CARENCIA);
        return armazenamentos.stream()
                .flatMap(a -> a.listarTodos().stream().map(o -> new Orfao(a.provedor(), o)))
                .filter(o -> !referenciadas.contains(o.objeto().chave()))
                .filter(o -> o.objeto().criadoEm().isBefore(antesDe))
                .toList();
    }
}
