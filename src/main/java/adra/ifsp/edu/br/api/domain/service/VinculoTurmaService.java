package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Centraliza a criação/encerramento de vínculos em TurmaAlunos. Usado tanto
 * pelo cadastro/edição de assistido (AssistidoService, que continua deixando
 * o usuário escolher a turma direto no formulário) quanto pelo botão
 * "Vincular alunos" da tela de turmas (TurmaService) — os dois caminhos que
 * mexem em Assistido.turma precisam manter essa tabela em dia, senão o
 * roster da chamada (CA-65, que lê só de TurmaAlunos) fica vazio pra quem
 * entrou por eles.
 *
 * Assistido.turma continua existindo e sendo mantido em sincronia (várias
 * outras telas/queries — contagem de alunos, filtro por turma no cadastro de
 * assistidos, dashboards — ainda leem esse ponteiro direto). TurmaAlunos é
 * quem virou a fonte de verdade histórica; Assistido.turma é um cache do
 * vínculo "atual" pra não precisar reescrever todo o resto do sistema agora.
 */
@Service
@RequiredArgsConstructor
public class VinculoTurmaService {

    private final TurmaAlunosRepository turmaAlunosRepository;

    /**
     * Encerra o(s) vínculo(s) ativo(s) do assistido (em qualquer turma) e abre
     * um novo vínculo ativo na turma informada. Se o assistido já estiver
     * vinculado ativamente a essa mesma turma, não faz nada (evita linha
     * duplicada num reenvio). turma == null só encerra o vínculo atual, sem
     * abrir um novo (assistido fica sem turma).
     */
    public void vincular(Assistido assistido, Turma turma) {
        List<TurmaAlunos> ativos = turmaAlunosRepository
                .findByAssistidoAndStatusAndDataSaidaIsNull(assistido, StatusGeral.ATIVO);

        boolean jaVinculadoNaTurma = turma != null && ativos.stream()
                .anyMatch(v -> v.getTurma().getTurmaId().equals(turma.getTurmaId()));

        if (jaVinculadoNaTurma) {
            return;
        }

        LocalDate hoje = LocalDate.now();

        for (TurmaAlunos vinculoAntigo : ativos) {
            vinculoAntigo.setDataSaida(hoje);
            vinculoAntigo.setStatus(StatusGeral.INATIVO);
            turmaAlunosRepository.save(vinculoAntigo);
        }

        if (turma != null) {
            TurmaAlunos novo = new TurmaAlunos();
            novo.setTurma(turma);
            novo.setAssistido(assistido);
            novo.setDataEntrada(hoje);
            novo.setStatus(StatusGeral.ATIVO);
            turmaAlunosRepository.save(novo);
        }
    }
}
