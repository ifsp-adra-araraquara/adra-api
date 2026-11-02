package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.NotaCalendario;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NotaCalendarioRepository;
import adra.ifsp.edu.br.api.exception.AutenticacaoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NotaCalendarioService {

    private final NotaCalendarioRepository notaCalendarioRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    public NotaCalendarioResponseDTO cadastrar(NotaCalendarioRequestDTO dto) {
        NotaCalendario nota = new NotaCalendario();
        nota.setDataNota(dto.dataNota());
        nota.setHorario(dto.horario());
        nota.setTitulo(dto.titulo().trim());
        nota.setDescricao(dto.descricao() != null && !dto.descricao().isBlank() ? dto.descricao().trim() : null);
        nota.setCriadoPor(obterUsuarioAutenticadoSeExistir());

        NotaCalendario salva = notaCalendarioRepository.save(nota);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "nota_calendario",
                salva.getNotaId(),
                AcaoSistema.CRIAR,
                null,
                mapaAuditoria(salva),
                "Cadastro de nota global no calendário"
        );

        return NotaCalendarioResponseDTO.fromEntity(salva);
    }

    public NotaCalendarioResponseDTO atualizar(Long id, NotaCalendarioRequestDTO dto) {
        NotaCalendario nota = buscarEntidadePorId(id);
        Map<String, Object> valorAnterior = mapaAuditoria(nota);

        nota.setDataNota(dto.dataNota());
        nota.setHorario(dto.horario());
        nota.setTitulo(dto.titulo().trim());
        nota.setDescricao(dto.descricao() != null && !dto.descricao().isBlank() ? dto.descricao().trim() : null);

        NotaCalendario salva = notaCalendarioRepository.save(nota);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "nota_calendario",
                salva.getNotaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                mapaAuditoria(salva),
                "Edição de nota global no calendário"
        );

        return NotaCalendarioResponseDTO.fromEntity(salva);
    }

    @Transactional(readOnly = true)
    public List<NotaCalendarioResponseDTO> listar(LocalDate dataInicio, LocalDate dataFim) {
        List<NotaCalendario> notas;
        if (dataInicio != null && dataFim != null) {
            notas = notaCalendarioRepository.findByDataNotaBetweenOrderByDataNotaAscHorarioAsc(dataInicio, dataFim);
        } else {
            notas = notaCalendarioRepository.findAllByOrderByDataNotaAscHorarioAsc();
        }
        return notas.stream().map(NotaCalendarioResponseDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public NotaCalendarioResponseDTO buscarPorId(Long id) {
        return NotaCalendarioResponseDTO.fromEntity(buscarEntidadePorId(id));
    }

    public void deletar(Long id) {
        NotaCalendario nota = buscarEntidadePorId(id);
        Map<String, Object> valorAnterior = mapaAuditoria(nota);

        notaCalendarioRepository.delete(nota);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "nota_calendario",
                id,
                AcaoSistema.EXCLUIR,
                valorAnterior,
                null,
                "Exclusão de nota global no calendário"
        );
    }

    private NotaCalendario buscarEntidadePorId(Long id) {
        return notaCalendarioRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Nota do calendário não encontrada: id " + id));
    }

    private Usuario obterUsuarioAutenticadoSeExistir() {
        try {
            return usuarioAutenticadoService.getUsuarioAtual();
        } catch (AutenticacaoException e) {
            return null;
        }
    }

    private Map<String, Object> mapaAuditoria(NotaCalendario nota) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("dataNota", nota.getDataNota().toString());
        mapa.put("horario", nota.getHorario() != null ? nota.getHorario().toString() : null);
        mapa.put("titulo", nota.getTitulo());
        mapa.put("descricao", nota.getDescricao());
        return mapa;
    }
}
