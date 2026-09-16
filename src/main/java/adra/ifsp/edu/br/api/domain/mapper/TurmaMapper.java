package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.turma.CriacaoTurmaDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TurmaMapper {

    private final OficinaRepository oficinaRepository;
    private final UsuarioRepository usuarioRepository;

    public Turma paraNovaEntidade(TurmaRequestDTO dto) {
        Turma nova = new Turma();

        if (dto.oficinaId() != null) {
            Oficina oficina = oficinaRepository.findById(dto.oficinaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Oficina não encontrada: id " + dto.oficinaId()));
            nova.setOficina(oficina);
        }

        if (dto.oficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.oficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.oficineiroResponsavelId()));
            nova.setOficineiroResponsavel(oficineiro);
        }

        nova.setNomeTurma(dto.nomeTurma());
        nova.setTurno(dto.turno());
        nova.setFaixaEtaria(dto.faixaEtaria());
        nova.setCapacidade(dto.capacidade());
        nova.setObservacoes(dto.observacoes());
        nova.setAtivo(true);

        return nova;
    }

    public Turma paraNovaEntidadeComHorario(CriacaoTurmaDTO dto) {
        Turma nova = new Turma();

        if (dto.getOficinaId() != null) {
            Oficina oficina = oficinaRepository.findById(dto.getOficinaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Oficina não encontrada: id " + dto.getOficinaId()));
            nova.setOficina(oficina);
        }

        if (dto.getOficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.getOficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.getOficineiroResponsavelId()));
            nova.setOficineiroResponsavel(oficineiro);
        }

        nova.setNomeTurma(dto.getNomeTurma());
        nova.setTurno(dto.getTurno());
        nova.setFaixaEtaria(dto.getFaixaEtaria());
        nova.setCapacidade(dto.getCapacidade());
        nova.setObservacoes(dto.getObservacoes());
        nova.setDiasDaSemana(dto.getDiasDaSemana());
        nova.setHorarioInicio(dto.getHorarioInicio());
        nova.setHorarioFim(dto.getHorarioFim());
        nova.setAtivo(true);

        return nova;
    }

    public void atualizarEntidade(Turma turma, TurmaRequestDTO dto) {
        if (dto.oficinaId() != null) {
            Oficina oficina = oficinaRepository.findById(dto.oficinaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Oficina não encontrada: id " + dto.oficinaId()));
            turma.setOficina(oficina);
        }

        if (dto.oficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.oficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.oficineiroResponsavelId()));
            turma.setOficineiroResponsavel(oficineiro);
        }

        turma.setNomeTurma(dto.nomeTurma());
        turma.setTurno(dto.turno());
        turma.setFaixaEtaria(dto.faixaEtaria());
        turma.setCapacidade(dto.capacidade());
        turma.setObservacoes(dto.observacoes());
    }

    public TurmaResponseDTO paraDTO(Turma turma) {
        return new TurmaResponseDTO(
                turma.getTurmaId(),
                turma.getOficina() != null ? turma.getOficina().getOficinaId() : null,
                turma.getOficineiroResponsavel() != null ? turma.getOficineiroResponsavel().getUsuarioId() : null,
                turma.getNomeTurma(),
                turma.getTurno(),
                turma.getFaixaEtaria(),
                turma.getCapacidade(),
                turma.getAtivo(),
                turma.getObservacoes()
        );
    }
}