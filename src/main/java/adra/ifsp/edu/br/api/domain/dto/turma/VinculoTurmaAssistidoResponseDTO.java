package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;

import java.time.LocalDate;

/**
 * Histórico de vínculos do assistido com turmas (tabela turma_aluno) — usado
 * na aba "Turmas" do modal do assistido, só pro coordenador. Mostra tanto
 * turmas em que o assistido já esteve (dataSaida preenchida / status
 * INATIVO) quanto a turma atual (dataSaida nula / status ATIVO).
 */
public record VinculoTurmaAssistidoResponseDTO(
        Long turmaId,
        String nomeTurma,
        Turno turno,
        Boolean turmaAtiva,
        LocalDate dataEntrada,
        LocalDate dataSaida,
        StatusGeral status
) {
    public static VinculoTurmaAssistidoResponseDTO fromEntity(TurmaAlunos vinculo) {
        return new VinculoTurmaAssistidoResponseDTO(
                vinculo.getTurma().getTurmaId(),
                vinculo.getTurma().getNomeTurma(),
                vinculo.getTurma().getTurno(),
                vinculo.getTurma().getAtivo(),
                vinculo.getDataEntrada(),
                vinculo.getDataSaida(),
                vinculo.getStatus()
        );
    }
}
