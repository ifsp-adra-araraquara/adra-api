package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.ComparacaoRematriculaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.RematricularRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.mapper.ComparacaoCadastro;
import adra.ifsp.edu.br.api.domain.mapper.PreCadastroParaAssistido;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Matricula;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Rematrícula: a família de quem já é atendido preenche o mesmo formulário;
 * a equipe vê o que mudou e abre a matrícula do ano para a ficha existente.
 * A ficha não é sobrescrita — atualizar dados é decisão manual da equipe.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RematriculaService {

    private final PreCadastroRepository preCadastroRepository;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoResponsavelRepository assistidoResponsavelRepository;
    private final TurmaRepository turmaRepository;
    private final MatriculaService matriculaService;
    private final VinculoTurmaService vinculoTurmaService;
    private final AnexosDoPreCadastro anexosDoPreCadastro;
    private final PreCadastroParaAssistido paraAssistido;
    private final ComparacaoCadastro comparacao;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @Transactional(readOnly = true)
    public ComparacaoRematriculaDTO comparar(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        Assistido assistido = atendidoDoCpf(registro);
        AssistidoRequestDTO enviado = paraAssistido.montar(registro, LocalDate.now());
        Turma grupoAtual = assistido.getTurma();

        return new ComparacaoRematriculaDTO(
                assistido.getAssistidoId(),
                assistido.getNomeCompleto(),
                assistido.getStatus(),
                registro.getAnoReferencia(),
                matriculaService.jaMatriculado(assistido.getAssistidoId(), registro.getAnoReferencia()),
                grupoAtual != null ? grupoAtual.getTurmaId() : null,
                grupoAtual != null ? grupoAtual.getNomeTurma() : null,
                comparacao.comparar(assistido,
                        assistidoResponsavelRepository.findByIdAssistidoId(assistido.getAssistidoId()), enviado));
    }

    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public PreCadastroResponseDTO rematricular(Long preCadastroId, RematricularRequestDTO dto) {
        PreCadastro registro = buscar(preCadastroId);
        if (!registro.getStatus().emAvaliacao()) {
            throw new RegraNegocioException("Este pré-cadastro já foi revisado.");
        }
        Assistido assistido = atendidoDoCpf(registro);
        int ano = registro.getAnoReferencia();
        if (matriculaService.jaMatriculado(assistido.getAssistidoId(), ano)) {
            throw new RegraNegocioException(assistido.getNomeCompleto() + " já está matriculado(a) em " + ano
                    + ". Reprove este envio se for repetido.");
        }
        Turma turma = grupoAtivo(dto.turmaId());

        boolean voltou = assistido.getStatus() == StatusGeral.INATIVO;
        LocalDate saidaAnterior = assistido.getDataSaida() != null ? assistido.getDataSaida() : LocalDate.now();
        if (voltou) {
            // Rematriculado = volta a ser atendido; a saída anterior fica no histórico de vínculos.
            assistido.setStatus(StatusGeral.ATIVO);
            assistido.setDataSaida(null);
            assistido.setMotivoSaida(null);
        }

        AssistidoRequestDTO enviado = paraAssistido.montar(registro, LocalDate.now());
        Matricula matricula = matriculaService.abrir(assistido, ano, enviado, preCadastroId);
        if (voltou) {
            vinculoTurmaService.encerrarVinculosAnteriores(assistido, matricula, saidaAnterior);
        }
        vinculoTurmaService.vincularNaMatricula(assistido, turma, matricula);
        if (assistido.getMatriculaAtual() == matricula) {
            assistido.setTurma(turma); // grupo "de agora"; para o ano seguinte, troca na virada do ano
        }
        anexosDoPreCadastro.transferir(preCadastroId, assistido.getAssistidoId(), matricula.getMatriculaId());

        String statusAnterior = registro.getStatus().name();
        registro.setStatus(StatusPreCadastro.CONVERTIDO);
        registro.setAssistidoId(assistido.getAssistidoId());
        registro.setRevisadoPor(usuarioAutenticadoService.getUsuarioAtual());
        registro.setRevisadoEm(OffsetDateTime.now());

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", statusAnterior),
                Map.of("status", "CONVERTIDO", "assistidoId", assistido.getAssistidoId(), "ano", ano,
                        "turmaId", turma.getTurmaId()),
                "Rematrícula " + ano);
        return PreCadastroResponseDTO.de(registro, null, false, List.of(), List.of());
    }

    private Assistido atendidoDoCpf(PreCadastro registro) {
        if (registro.getCpf() == null) {
            throw new RegraNegocioException("Pré-cadastro sem CPF: não dá para identificar o atendido.");
        }
        return assistidoRepository.findByCpf(registro.getCpf())
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há atendido com este CPF. Use \"Aprovar e criar cadastro\"."));
    }

    private Turma grupoAtivo(Long turmaId) {
        Turma turma = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Grupo nao encontrado: id " + turmaId));
        if (!Boolean.TRUE.equals(turma.getAtivo())) {
            throw new RegraNegocioException("O grupo escolhido não está ativo.");
        }
        return turma;
    }

    private PreCadastro buscar(Long preCadastroId) {
        return preCadastroRepository.findById(preCadastroId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pré-cadastro não encontrado: id " + preCadastroId));
    }
}
