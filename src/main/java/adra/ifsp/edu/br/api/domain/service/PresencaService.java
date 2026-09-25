package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.mapper.PresencaMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modelo esparso: só existe linha em Presenca quando o assistido FALTOU
 * (FALTA ou FALTA_JUSTIFICADA). PRESENTE nunca vira linha — é o estado
 * implícito de quem está no roster da turma e não tem falta registrada
 * naquela aula. Por causa disso:
 *  - reenvio marcando PRESENTE quem antes tinha falta APAGA o registro,
 *    não faz update (CA-65.4 vale pra esse "voltar a atrás" também);
 *  - o GET por aula precisa do roster (TurmaAlunosRepository) pra saber
 *    quem existia pra poder inferir quem faltou "por omissão".
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PresencaService {

    private final PresencaRepository presencaRepository;
    private final AulaRepository aulaRepository;
    private final AssistidoRepository assistidoRepository;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final PresencaMapper presencaMapper;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    /**
     * Lançamento em lote — endpoint principal do CA-65 (POST /api/chamadas/aula/{id}).
     * PRESENTE não gera linha (e apaga uma linha de falta de reenvio anterior,
     * se existir); FALTA/FALTA_JUSTIFICADA fazem upsert por (aula, assistido).
     */
    public List<PresencaResponseDTO> registrarChamada(Long idAula, List<PresencaRequestDTO> presencasDto) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        validarAulaRealizada(aula); // CA-65.3

        Map<Long, PresencaRequestDTO> dtoPorAssistidoId = presencasDto.stream()
                .collect(Collectors.toMap(
                        PresencaRequestDTO::assistidoId, dto -> dto, (a, b) -> b, LinkedHashMap::new));

        Map<Long, Assistido> assistidosPorId = assistidoRepository.findAllById(dtoPorAssistidoId.keySet()).stream()
                .collect(Collectors.toMap(Assistido::getAssistidoId, a -> a));

        Map<Long, Presenca> existentesPorAssistidoId = presencaRepository.findByAula(aula).stream()
                .collect(Collectors.toMap(p -> p.getAssistido().getAssistidoId(), p -> p));

        // CA-65.1 + CA-70.1/70.3: só dá pra lançar presença de quem estava
        // vinculado à turma na data da aula E não estava desligado antes/na
        // data dela (findElegiveisParaChamada já cruza os dois critérios).
        Set<Long> assistidosDaTurma = turmaAlunosRepository
                .findElegiveisParaChamada(aula.getTurma(), aula.getDataAula())
                .stream()
                .map(v -> v.getAssistido().getAssistidoId())
                .collect(Collectors.toSet());

        Usuario usuarioLogado = usuarioAutenticadoService.getUsuarioAtual();

        List<Presenca> paraSalvar = new ArrayList<>();
        List<Presenca> paraApagar = new ArrayList<>();

        for (Map.Entry<Long, PresencaRequestDTO> entrada : dtoPorAssistidoId.entrySet()) {
            Long assistidoId = entrada.getKey();
            PresencaRequestDTO dto = entrada.getValue();

            Assistido assistido = assistidosPorId.get(assistidoId);
            if (assistido == null) {
                throw new EntidadeNaoEncontradaException("Assistido não encontrado: id " + assistidoId);
            }

            if (!assistidosDaTurma.contains(assistidoId)) {
                throw new RegraNegocioException(
                        "Assistido id " + assistidoId + " não está vinculado à turma desta aula."
                );
            }

            Presenca existente = existentesPorAssistidoId.get(assistidoId);

            if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
                if (existente != null) {
                    paraApagar.add(existente); // reenvio "voltou" pra presente: some o registro de falta
                }
                continue; // presente nunca cria linha
            }

            Presenca presenca = existente != null ? existente : new Presenca();
            if (presenca.getPresencaId() == null) {
                presenca.setAula(aula);
                presenca.setAssistido(assistido);
                presenca.setCriadoPor(usuarioLogado);
            }
            presenca.setStatusPresenca(dto.statusPresenca());
            presenca.setAtualizadoPor(usuarioLogado);
            paraSalvar.add(presenca);
        }

        if (!paraApagar.isEmpty()) {
            presencaRepository.deleteAll(paraApagar); // cascade apaga falta_justificada junto
        }

        List<Presenca> salvas = presencaRepository.saveAll(paraSalvar);

        // satélite de falta justificada — precisa do presencaId já persistido (CA-65.2)
        salvas.forEach(presenca -> presencaMapper.sincronizarFaltaJustificada(
                presenca, dtoPorAssistidoId.get(presenca.getAssistido().getAssistidoId())
        ));

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "chamada",
                idAula,
                AcaoSistema.CRIAR,
                null,
                Map.of("quantidadeFaltas", String.valueOf(salvas.size())),
                "Registro de chamada em lote"
        );

        // resposta ecoa os 3 status pro cliente, mesmo os PRESENTE não tendo linha
        Map<Long, Presenca> salvasPorAssistidoId = salvas.stream()
                .collect(Collectors.toMap(p -> p.getAssistido().getAssistidoId(), p -> p));

        return dtoPorAssistidoId.keySet().stream()
                .map(assistidoId -> {
                    PresencaRequestDTO dto = dtoPorAssistidoId.get(assistidoId);
                    if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
                        return PresencaResponseDTO.presente(
                                idAula, assistidoId, assistidosPorId.get(assistidoId).getNomeCompleto()
                        );
                    }
                    return presencaMapper.paraDTO(salvasPorAssistidoId.get(assistidoId));
                })
                .toList();
    }

    /** Lançamento/edição de uma presença isolada — mesma regra esparsa do lote, num item só. */
    public PresencaResponseDTO registrarPresenca(PresencaRequestDTO dto) {
        Aula aula = aulaRepository.findById(dto.aulaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + dto.aulaId()));
        Assistido assistido = assistidoRepository.findById(dto.assistidoId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + dto.assistidoId()));

        validarAulaRealizada(aula); // CA-65.3

        // CA-65.1 + CA-70.1/70.3
        boolean pertenceATurma = turmaAlunosRepository
                .findElegiveisParaChamada(aula.getTurma(), aula.getDataAula())
                .stream()
                .anyMatch(v -> v.getAssistido().getAssistidoId().equals(assistido.getAssistidoId()));
        if (!pertenceATurma) {
            throw new RegraNegocioException(
                    "Assistido não está vinculado à turma desta aula, ou já estava desligado na data."
            );
        }

        Optional<Presenca> existente = presencaRepository.findByAulaAndAssistido(aula, assistido);

        if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
            existente.ifPresent(presencaRepository::delete);
            auditoriaService.registrar(
                    ModuloSistema.CHAMADA, "presenca", null, AcaoSistema.EDITAR, null,
                    Map.of("aulaId", aula.getAulaId().toString(), "assistidoId", assistido.getAssistidoId().toString(),
                            "statusPresenca", "PRESENTE"),
                    "Registro de presença (sem linha — presente)"
            );
            return PresencaResponseDTO.presente(aula.getAulaId(), assistido.getAssistidoId(), assistido.getNomeCompleto());
        }

        Usuario usuarioLogado = usuarioAutenticadoService.getUsuarioAtual();

        Presenca presenca = existente.orElseGet(() -> {
            Presenca nova = new Presenca();
            nova.setAula(aula);
            nova.setAssistido(assistido);
            nova.setCriadoPor(usuarioLogado);
            return nova;
        });

        boolean eraNova = presenca.getPresencaId() == null;
        presenca.setStatusPresenca(dto.statusPresenca());
        presenca.setAtualizadoPor(usuarioLogado);
        presenca = presencaRepository.save(presenca);
        presencaMapper.sincronizarFaltaJustificada(presenca, dto); // CA-65.2

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                eraNova ? AcaoSistema.CRIAR : AcaoSistema.EDITAR,
                null,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString(),
                        "statusPresenca", presenca.getStatusPresenca().name()
                ),
                "Registro de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    /**
     * GET /api/chamadas/aula/{id} — a chamada completa: roster da turma na data
     * da aula (TurmaAlunos) cruzado com as faltas registradas. Quem não tem
     * falta = esteve presente.
     */
    public List<PresencaResponseDTO> buscarPorAula(Long idAula) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        List<TurmaAlunos> vinculosAtivos = turmaAlunosRepository.findElegiveisParaChamada(
                aula.getTurma(), aula.getDataAula());

        List<Presenca> faltas = presencaRepository.findByAula(aula);

        return presencaMapper.montarChamadaCompleta(idAula, vinculosAtivos, faltas);
    }

    /**
     * Histórico de um assistido — no modelo esparso isso só devolve as faltas
     * dele (não tem linha pra reconstituir "presente" sem varrer todas as aulas
     * de todas as turmas por onde ele passou). Se precisar de frequência
     * completa por assistido, avisa que a lógica é parecida com buscarPorAula,
     * só que iterando as aulas da(s) turma(s) do período em vez de uma só.
     */
    public List<PresencaResponseDTO> buscarPorAssistido(Long idAssistido) {
        Assistido assistido = assistidoRepository.findById(idAssistido)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + idAssistido));

        return presencaMapper.paraDTOList(presencaRepository.findByAssistido(assistido));
    }

    /** No modelo esparso, "atualizar pra PRESENTE" apaga a linha em vez de guardar um status. */
    public PresencaResponseDTO atualizarPresenca(Long id, PresencaRequestDTO dto) {
        Presenca presenca = buscarEntidadePorId(id);
        validarAulaRealizada(presenca.getAula()); // CA-65.3 vale pra edição também

        // CA-70.3: "corrigir" chamada de quem já estava desligado na data da
        // aula também é rejeitado (não só o lançamento novo via POST). Aulas
        // anteriores à saída continuam elegíveis, então isso não trava
        // correção de histórico (CA-70.2).
        Long assistidoIdDaPresenca = presenca.getAssistido().getAssistidoId();
        boolean elegivel = turmaAlunosRepository
                .findElegiveisParaChamada(presenca.getAula().getTurma(), presenca.getAula().getDataAula())
                .stream()
                .anyMatch(v -> v.getAssistido().getAssistidoId().equals(assistidoIdDaPresenca));
        if (!elegivel) {
            throw new RegraNegocioException(
                    "Assistido não está vinculado à turma desta aula, ou já estava desligado na data."
            );
        }

        if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
            Long aulaId = presenca.getAula().getAulaId();
            Long assistidoId = presenca.getAssistido().getAssistidoId();
            String nomeCompleto = presenca.getAssistido().getNomeCompleto();

            presencaRepository.delete(presenca);

            auditoriaService.registrar(
                    ModuloSistema.CHAMADA, "presenca", id, AcaoSistema.EXCLUIR,
                    Map.of("statusPresencaAnterior", presenca.getStatusPresenca().name()),
                    Map.of("statusPresenca", "PRESENTE"),
                    "Atualização de presença (virou presente — linha removida)"
            );

            return PresencaResponseDTO.presente(aulaId, assistidoId, nomeCompleto);
        }

        Map<String, Object> valorAnterior = Map.of("statusPresenca", presenca.getStatusPresenca().name());

        presencaMapper.atualizarEntidade(presenca, dto);
        presenca.setAtualizadoPor(usuarioAutenticadoService.getUsuarioAtual());
        presenca = presencaRepository.save(presenca);
        presencaMapper.sincronizarFaltaJustificada(presenca, dto);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of("statusPresenca", presenca.getStatusPresenca().name()),
                "Atualização de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    /** Apagar uma falta é, por natureza, o mesmo que dizer que o assistido esteve presente. */
    public void deletarPresenca(Long id) {
        Presenca presenca = buscarEntidadePorId(id);
        presencaRepository.delete(presenca); // falta_justificada some junto (ON DELETE CASCADE na migration)

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EXCLUIR,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString()
                ),
                null,
                "Exclusão de presença"
        );
    }

    private void validarAulaRealizada(Aula aula) {
        if (aula.getStatusAula() != StatusAula.REALIZADA) {
            throw new RegraNegocioException(
                    "Só é possível lançar chamada em aulas com status REALIZADA (status atual: "
                            + aula.getStatusAula() + ")."
            );
        }
    }

    private Presenca buscarEntidadePorId(Long id) {
        return presencaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Presença não encontrada: id " + id));
    }
}
