package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Matricula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
     * Troca o grupo do atendido na matrícula atual: encerra o vínculo aberto
     * dessa matrícula e abre um novo no grupo informado. Já vinculado ao mesmo
     * grupo: não faz nada. turma == null só encerra (atendido fica sem grupo).
     */
    public void vincular(Assistido assistido, Turma turma) {
        vincularNaMatricula(assistido, turma, assistido.getMatriculaAtual());
    }

    /**
     * Grupo do atendido numa matrícula específica (ex.: rematrícula para o ano
     * seguinte). Só mexe nos vínculos dessa matrícula: o grupo do ano em curso
     * continua valendo até o fim do ano. O vínculo começa hoje ou, para um ano
     * que ainda não começou, em 1º de janeiro dele.
     */
    public void vincularNaMatricula(Assistido assistido, Turma turma, Matricula matricula) {
        List<TurmaAlunos> ativos = turmaAlunosRepository
                .findByAssistidoAndStatusAndDataSaidaIsNull(assistido, StatusGeral.ATIVO).stream()
                .filter(vinculo -> mesmaMatricula(vinculo, matricula))
                .toList();

        boolean jaVinculadoNaTurma = turma != null && ativos.stream()
                .anyMatch(v -> v.getTurma().getTurmaId().equals(turma.getTurmaId()));

        if (jaVinculadoNaTurma) {
            return;
        }

        LocalDate hoje = LocalDate.now();

        for (TurmaAlunos vinculoAntigo : ativos) {
            if (vinculoAntigo.getDataEntrada().isAfter(hoje)) {
                // Ainda nem começou (ano seguinte): trocar de grupo é corrigir, não encerrar.
                turmaAlunosRepository.delete(vinculoAntigo);
                continue;
            }
            vinculoAntigo.setDataSaida(hoje);
            vinculoAntigo.setStatus(StatusGeral.INATIVO);
            turmaAlunosRepository.saveAndFlush(vinculoAntigo);
        }

        if (turma != null) {
            TurmaAlunos novo = new TurmaAlunos();
            novo.setTurma(turma);
            novo.setAssistido(assistido);
            novo.setMatricula(matricula); // grupo do ano da matrícula
            novo.setDataEntrada(inicioDoVinculo(matricula, hoje));
            novo.setStatus(StatusGeral.ATIVO);
            turmaAlunosRepository.save(novo);
        }
    }

    /**
     * Fecha os vínculos ainda abertos de matrículas anteriores à informada
     * (quem estava desligado e voltou na rematrícula), na data de saída dele.
     */
    public void encerrarVinculosAnteriores(Assistido assistido, Matricula matricula, LocalDate saida) {
        turmaAlunosRepository.findByAssistidoAndStatusAndDataSaidaIsNull(assistido, StatusGeral.ATIVO).stream()
                .filter(vinculo -> vinculo.getMatricula() == null || vinculo.getMatricula().getAno() < matricula.getAno())
                .forEach(vinculo -> {
                    vinculo.setDataSaida(saida.isBefore(vinculo.getDataEntrada()) ? vinculo.getDataEntrada() : saida);
                    vinculo.setStatus(StatusGeral.INATIVO);
                });
    }

    static LocalDate inicioDoVinculo(Matricula matricula, LocalDate hoje) {
        if (matricula == null) {
            return hoje;
        }
        LocalDate inicioDoAno = LocalDate.of(matricula.getAno(), 1, 1);
        return hoje.isBefore(inicioDoAno) ? inicioDoAno : hoje;
    }

    /** Vínculo sem matrícula (anterior à matrícula anual) conta como da matrícula atual. */
    private static boolean mesmaMatricula(TurmaAlunos vinculo, Matricula matricula) {
        if (vinculo.getMatricula() == null || matricula == null) {
            return true;
        }
        return Objects.equals(vinculo.getMatricula().getMatriculaId(), matricula.getMatriculaId());
    }
}
