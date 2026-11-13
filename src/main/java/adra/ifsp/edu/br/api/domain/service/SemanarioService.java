package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.semanario.SemanarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.semanario.SemanarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Semanario;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.SemanarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class SemanarioService {

    private final SemanarioRepository semanarioRepository;
    private final OficinaTurmaRepository oficinaTurmaRepository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;
    private final ArquivoRepository arquivoRepository;
    private final ArquivoService arquivoService;

    /** Um semanário por oficina, grupo e semana: enviar de novo na mesma semana atualiza o plano. */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true) // aviso de semanário pendente
    public SemanarioResponseDTO enviar(SemanarioRequestDTO dto) {
        OficinaTurma vinculo = buscarVinculoAtivo(dto.oficinaId(), dto.turmaId());
        LocalDate segunda = dto.semana().with(DayOfWeek.MONDAY);

        Semanario semanario = semanarioRepository
                .findByOficina_OficinaIdAndTurma_TurmaIdAndSemanaInicio(dto.oficinaId(), dto.turmaId(), segunda)
                .orElseGet(() -> novo(vinculo, segunda));
        boolean ehNovo = semanario.getSemanarioId() == null;

        semanario.setConteudoPrevisto(dto.conteudoPrevisto().trim());
        semanario.setObjetivos(textoOuNulo(dto.objetivos()));
        semanario.setRecursosNecessarios(textoOuNulo(dto.recursosNecessarios()));
        semanario.setEnviadoPor(usuarioAutenticadoService.getUsuarioAtual());
        semanario = semanarioRepository.save(semanario);

        auditoriaService.registrar(
                ModuloSistema.SEMANARIOS,
                "semanario",
                semanario.getSemanarioId(),
                ehNovo ? AcaoSistema.CRIAR : AcaoSistema.EDITAR,
                null,
                Map.of("oficinaId", dto.oficinaId(), "turmaId", dto.turmaId(), "semanaInicio", segunda.toString()),
                ehNovo ? "Envio de semanário" : "Atualização de semanário"
        );

        return SemanarioResponseDTO.de(semanario);
    }

    /** Remove o semanário e o material anexado a ele. */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public void remover(Long semanarioId) {
        Semanario semanario = semanarioRepository.findById(semanarioId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Semanário não encontrado: id " + semanarioId));

        arquivoRepository.findByEntidadeTipoAndEntidadeIdAndExcluidoEmIsNullOrderByCriadoEmDesc(EntidadeArquivo.SEMANARIO, semanarioId)
                .forEach(arquivo -> arquivoService.remover(arquivo.getArquivoId()));
        semanarioRepository.delete(semanario);

        auditoriaService.registrar(
                ModuloSistema.SEMANARIOS,
                "semanario",
                semanarioId,
                AcaoSistema.EXCLUIR,
                Map.of("semanaInicio", semanario.getSemanaInicio().toString()),
                null,
                "Remoção de semanário"
        );
    }

    @Transactional(readOnly = true)
    public List<SemanarioResponseDTO> listarTodos() {
        return semanarioRepository.findAllByOrderBySemanaInicioDescAtualizadoEmDesc().stream()
                .map(SemanarioResponseDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SemanarioResponseDTO> listarPorOficina(Long oficinaId) {
        return semanarioRepository.findByOficina_OficinaIdOrderBySemanaInicioDesc(oficinaId).stream()
                .map(SemanarioResponseDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SemanarioResponseDTO> listarDoOficineiroAtual() {
        Long oficineiroId = usuarioAutenticadoService.getUsuarioAtual().getUsuarioId();
        return semanarioRepository.findByOficina_OficineiroResponsavel_UsuarioIdOrderBySemanaInicioDesc(oficineiroId).stream()
                .map(SemanarioResponseDTO::de)
                .toList();
    }

    private OficinaTurma buscarVinculoAtivo(Long oficinaId, Long turmaId) {
        return oficinaTurmaRepository.findByOficina_OficinaIdAndTurma_TurmaIdAndAtivoTrue(oficinaId, turmaId)
                .orElseThrow(() -> new RegraNegocioException("Esta oficina não atende o grupo informado."));
    }

    private Semanario novo(OficinaTurma vinculo, LocalDate segunda) {
        Semanario semanario = new Semanario();
        semanario.setOficina(vinculo.getOficina());
        semanario.setTurma(vinculo.getTurma());
        semanario.setSemanaInicio(segunda);
        return semanario;
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
