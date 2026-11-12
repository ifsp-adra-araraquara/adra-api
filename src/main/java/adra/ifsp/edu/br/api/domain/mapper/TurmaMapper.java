package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.FaixaEtaria;
import adra.ifsp.edu.br.api.domain.model.Turma;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TurmaMapper {

    public Turma paraNovaEntidade(TurmaRequestDTO dto, FaixaEtaria faixaEtaria) {
        Turma nova = new Turma();

        nova.setNomeTurma(dto.nomeTurma());
        nova.setTurno(dto.turno());
        nova.setFaixaEtaria(faixaEtaria);
        nova.setCapacidade(dto.capacidade());
        nova.setObservacoes(dto.observacoes());
        nova.setDiasDaSemana(dto.diasDaSemana());
        nova.setAtivo(true);

        return nova;
    }

    public void atualizarEntidade(Turma turma, TurmaRequestDTO dto, FaixaEtaria faixaEtaria) {
        turma.setNomeTurma(dto.nomeTurma());
        turma.setTurno(dto.turno());
        turma.setFaixaEtaria(faixaEtaria);
        turma.setCapacidade(dto.capacidade());
        turma.setObservacoes(dto.observacoes());
        if (dto.diasDaSemana() != null) {
            turma.setDiasDaSemana(dto.diasDaSemana());
        }
    }

    /** Ver TurmaResponseDTO#fromEntity — contagem e oficinas já resolvidas pelo chamador. */
    public TurmaResponseDTO paraDTO(Turma turma, Integer quantidadeAlunos, List<Long> oficinaIds) {
        return TurmaResponseDTO.fromEntity(turma, quantidadeAlunos, oficinaIds);
    }
}
