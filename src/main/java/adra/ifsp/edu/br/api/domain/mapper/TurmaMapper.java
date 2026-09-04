package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Turma;
import org.springframework.stereotype.Component;

@Component
public class TurmaMapper {

    private TurmaMapper() {
        // classe utilitária, não deve ser instanciada
    }
    public static void atualizarEntidade(Turma turma, TurmaRequestDTO dto) {
        turma.setNomeTurma(dto.nomeTurma());
        turma.setTurno(dto.turno());
        turma.setFaixaEtaria(dto.faixaEtaria());
        turma.setCapacidade(dto.capacidade());
        turma.setObservacoes(dto.observacoes());
    }
    public static Turma paraNovaEntidade(TurmaRequestDTO dto) {
        Turma nova = new Turma();

        nova.setNomeTurma(dto.nomeTurma());
        nova.setTurno(dto.turno());
        nova.setFaixaEtaria(dto.faixaEtaria());
        nova.setCapacidade(dto.capacidade());
        nova.setObservacoes(dto.observacoes());
        nova.setAtivo(true);

        return nova;
    }

    public static TurmaResponseDTO paraDTO(Turma turma) {
        return new TurmaResponseDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                turma.getTurno(),
                turma.getFaixaEtaria(),
                turma.getCapacidade(),
                turma.getAtivo(),
                turma.getObservacoes()
        );
    }
}