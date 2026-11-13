package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoAnoDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoPreviaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoPreviaDTO.AtendidoNaoRenovouDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.EncerramentoAno;
import adra.ifsp.edu.br.api.domain.repository.EncerramentoAnoRepository;
import adra.ifsp.edu.br.api.domain.repository.MatriculaRepository;
import adra.ifsp.edu.br.api.domain.repository.PeriodoMatriculaRepository;
import adra.ifsp.edu.br.api.domain.repository.SolicitacaoDesligamentoRepository;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Encerramento do ano letivo, feito à mão pelo Coordenador depois do prazo de
 * rematrícula. Numa transação só:
 * <ol>
 *   <li>quem não renovou perde a vaga (desligado no último dia do ano);</li>
 *   <li>quem renovou passa a ter a matrícula e o grupo do ano seguinte;</li>
 *   <li>os vínculos de grupo do ano fecham em 31/12;</li>
 *   <li>as matrículas do ano viram ENCERRADA e o ano fica travado;</li>
 *   <li>os registros disciplinares do ano são arquivados (a contagem recomeça).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EncerramentoAnoService {

    private final MatriculaRepository matriculaRepository;
    private final EncerramentoAnoRepository encerramentoRepository;
    private final PeriodoMatriculaRepository periodoRepository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;
    private final DisciplinarService disciplinarService;
    private final SolicitacaoDesligamentoRepository solicitacaoDesligamentoRepository;

    /**
     * Ano que está esperando encerramento (o mais antigo com matrícula ativa), só quando
     * já dá para encerrar: durante o ano, sem a matrícula do seguinte aberta, não há o que avisar.
     */
    @Transactional(readOnly = true)
    public Optional<EncerramentoPreviaDTO> pendente() {
        Integer ano = matriculaRepository.menorAnoComMatriculaAtiva(LocalDate.now().getYear());
        if (ano == null || motivoBloqueio(ano) != null) {
            return Optional.empty();
        }
        return Optional.of(previa(ano));
    }

    @Transactional(readOnly = true)
    public EncerramentoPreviaDTO previa(int ano) {
        List<AtendidoNaoRenovouDTO> quemNaoRenovou = matriculaRepository.listarNaoRenovaram(ano).stream()
                .map(a -> new AtendidoNaoRenovouDTO(a.getAssistidoId(), a.getNomeCompleto(), a.getNomeTurma()))
                .toList();
        String bloqueio = motivoBloqueio(ano);
        DisciplinarService.PreviaArquivamentoAno disciplinar = disciplinarService.previaArquivamentoDoAno(ano);
        return new EncerramentoPreviaDTO(ano,
                matriculaRepository.contarAtivas(ano),
                matriculaRepository.contarRenovaram(ano),
                quemNaoRenovou.size(),
                matriculaRepository.contarNovosNoAnoSeguinte(ano),
                quemNaoRenovou,
                bloqueio == null,
                bloqueio,
                disciplinar.registros(),
                disciplinar.documentosPendentes(),
                disciplinar.consequenciasPendentes());
    }

    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public EncerramentoAnoDTO encerrar(int ano) {
        String bloqueio = motivoBloqueio(ano);
        if (bloqueio != null) {
            throw new RegraNegocioException(bloqueio);
        }
        LocalDate ultimoDia = LocalDate.of(ano, 12, 31);
        int ativas = matriculaRepository.contarAtivas(ano);
        int renovaram = matriculaRepository.contarRenovaram(ano);

        // A ordem importa: os passos 1 e 2 ainda dependem das matrículas ATIVA do ano.
        int naoRenovaram = matriculaRepository.desligarQuemNaoRenovou(ano, ultimoDia,
                "Não renovou a matrícula para " + (ano + 1));
        matriculaRepository.passarParaOAnoSeguinte(ano);
        int vinculosFechados = matriculaRepository.fecharVinculosDoAno(ano, ultimoDia);
        matriculaRepository.encerrarMatriculasDoAno(ano);
        // Quem perdeu a vaga saiu: pedido de desligamento em aberto não tem mais o que decidir.
        solicitacaoDesligamentoRepository.cancelarPendentesDeInativos("Desligado no encerramento do ano de " + ano + ".");

        Usuario coordenador = usuarioAutenticadoService.getUsuarioAtual();
        // A contagem disciplinar recomeça no ano seguinte; o histórico fica (arquivado).
        int disciplinarArquivados = disciplinarService.arquivarNoEncerramentoDoAno(ano, coordenador);

        EncerramentoAno encerramento = encerramentoRepository.save(new EncerramentoAno(ano,
                coordenador, ativas, renovaram, naoRenovaram));

        auditoriaService.registrar(ModuloSistema.ASSISTIDOS, "encerramento_ano", (long) ano, AcaoSistema.EDITAR,
                null,
                Map.of("matriculasEncerradas", ativas, "renovaram", renovaram, "naoRenovaram", naoRenovaram,
                        "vinculosFechados", vinculosFechados, "disciplinarArquivados", disciplinarArquivados),
                "Encerramento do ano " + ano);
        return EncerramentoAnoDTO.de(encerramento);
    }

    @Transactional(readOnly = true)
    public List<EncerramentoAnoDTO> listar() {
        return encerramentoRepository.findAll().stream()
                .map(EncerramentoAnoDTO::de)
                .sorted((a, b) -> Integer.compare(b.ano(), a.ano()))
                .toList();
    }

    /**
     * Ano já encerrado ou ainda não chegado não fecha. O ano em curso só fecha
     * depois de aberta a matrícula do seguinte — senão todos perderiam a vaga.
     */
    private String motivoBloqueio(int ano) {
        int anoCorrente = LocalDate.now().getYear();
        if (encerramentoRepository.existsById(ano)) {
            return "O ano " + ano + " já foi encerrado.";
        }
        if (ano > anoCorrente) {
            return "O ano " + ano + " ainda não começou.";
        }
        if (ano == anoCorrente && !periodoRepository.existsById(ano + 1)) {
            return "Abra a matrícula de " + (ano + 1) + " antes de encerrar " + ano
                    + ": sem rematrícula, todos perderiam a vaga.";
        }
        return null;
    }
}
