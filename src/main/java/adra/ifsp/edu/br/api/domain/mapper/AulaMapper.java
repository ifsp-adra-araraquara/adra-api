package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaComDetalhesResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AulaMapper {

    private final TurmaRepository turmaRepository;

    public Aula paraNovaEntidade(AulaRequestDTO dto) {
        Aula aula = new Aula();
        atualizarEntidade(aula, dto);
        return aula;
    }

    public void atualizarEntidade(Aula aula, AulaRequestDTO dto) {
        if (dto.turmaId() != null) {
            Turma turma = turmaRepository.findById(dto.turmaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma não encontrada: id " + dto.turmaId()));
            aula.setTurma(turma);
        }
        aula.setTitulo(dto.titulo());
        aula.setDescricao(dto.descricao());
        aula.setDataAula(dto.dataAula());
        aula.setHorarioInicio(dto.horarioInicio());
        aula.setHorarioFim(dto.horarioFim());
        aula.setConteudoPrevisto(dto.conteudoPrevisto());
        aula.setConteudoMinistrado(dto.conteudoMinistrado());
        aula.setObjetivos(dto.objetivos());
        aula.setRecursosNecessarios(dto.recursosNecessarios());
        aula.setStatusAula(dto.statusAula());
        aula.setObservacoes(dto.observacoes());
    }

    public AulaResponseDTO paraDTO(Aula aula) {
        return AulaResponseDTO.fromEntity(aula);
    }

    public AulaComDetalhesResponseDTO paraDTOComDetalhes(Aula aula, Integer quantidadeAlunos) {
        return AulaComDetalhesResponseDTO.fromEntity(aula, quantidadeAlunos);
    }
}
