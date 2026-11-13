package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.aviso.AvisoDTO;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.PainelRepository;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.SemanarioRepository;
import adra.ifsp.edu.br.api.domain.repository.SolicitacaoDesligamentoRepository;
import adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FT-18: central de avisos. Junta o que precisa de ação e entrega a cada
 * perfil só o que ele resolve. Nada é guardado: o aviso some quando a
 * pendência de origem é resolvida.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvisoService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    /** O Educador Social vê o que muda o dia dele; o resto do disciplinar é do Coordenador. */
    private static final Set<TipoAviso> DISCIPLINARES_DO_EDUCADOR = Set.of(TipoAviso.SUSPENSO_HOJE, TipoAviso.RETORNO_COM_RESPONSAVEL);

    private final RegistroDisciplinarRepository registroRepository;
    private final StatusChamadaService statusChamadaService;
    private final ChamadaService chamadaService;
    private final PainelRepository painelRepository;
    private final PreCadastroRepository preCadastroRepository;
    private final AssistidoRepository assistidoRepository;
    private final SemanarioRepository semanarioRepository;
    private final SolicitacaoDesligamentoRepository solicitacaoDesligamentoRepository;

    @Cacheable(CacheConfig.PAINEL)
    public List<AvisoDTO> doPerfil(NomeNivelPermissao perfil) {
        LocalDate hoje = LocalDate.now();
        List<AvisoDTO> avisos = new ArrayList<>();
        switch (perfil) {
            case COORDENADOR -> {
                avisos.addAll(disciplinares(hoje));
                avisos.addAll(chamadasAtrasadas(hoje));
                avisos.addAll(faltasSeguidas(hoje));
                avisos.addAll(semanariosPendentes(hoje));
                avisos.addAll(cadastro());
                avisos.addAll(desligamentosPendentes());
            }
            case ASSISTENTE_SOCIAL -> {
                avisos.addAll(faltasSeguidas(hoje));
                avisos.addAll(cadastro());
            }
            case EDUCADOR_SOCIAL -> {
                avisos.addAll(disciplinares(hoje).stream().filter(a -> DISCIPLINARES_DO_EDUCADOR.contains(a.tipo())).toList());
                avisos.addAll(chamadasDeHoje(hoje));
            }
            case OFICINEIRO -> {
            }
        }
        return avisos;
    }

    /** Um aviso por atendido e tipo, mesmo que venha de mais de um registro. */
    private List<AvisoDTO> disciplinares(LocalDate hoje) {
        Map<String, AvisoDTO> porAtendidoETipo = new LinkedHashMap<>();
        for (RegistroDisciplinar r : registroRepository.findComPendenciaEm(hoje)) {
            String nome = r.getAssistido().getNomeCompleto();
            if (r.suspensoEm(hoje)) {
                adicionar(porAtendidoETipo, r, TipoAviso.SUSPENSO_HOJE,
                        nome + " está " + suspenso(r.getAssistido().getSexo()) + " até " + r.getSuspensaoFim().format(DIA_MES),
                        comGrupo(r, "não participa das atividades"));
            }
            if (r.temConsequenciaPendente()) {
                adicionar(porAtendidoETipo, r, tipoDaConsequencia(r.getConsequencia()),
                        tituloDaConsequencia(r.getConsequencia(), nome), comGrupo(r, motivoDaConsequencia(r.getConsequencia())));
            }
            if (r.getDocumentoStatus() == StatusDocumentoDisciplinar.PENDENTE_ASSINATURA) {
                adicionar(porAtendidoETipo, r, TipoAviso.DOCUMENTO_A_ASSINAR, "Documento a devolver assinado — " + nome,
                        rotuloDoNivel(r.getNivel()) + " de " + r.getDataRegistro().format(DIA_MES));
            }
        }
        return new ArrayList<>(porAtendidoETipo.values());
    }

    private static void adicionar(Map<String, AvisoDTO> avisos, RegistroDisciplinar r, TipoAviso tipo,
                                  String titulo, String detalhe) {
        Long assistidoId = r.getAssistido().getAssistidoId();
        avisos.putIfAbsent(tipo + ":" + assistidoId, new AvisoDTO(tipo, titulo, detalhe, assistidoId, null, null));
    }

    private static String suspenso(Sexo sexo) {
        if (sexo == Sexo.FEMININO) return "suspensa";
        if (sexo == Sexo.MASCULINO) return "suspenso";
        return "em suspensão";
    }

    /** "CJ Tarde · texto" quando o registro tem grupo; só o texto, com inicial maiúscula, quando não tem. */
    private static String comGrupo(RegistroDisciplinar r, String texto) {
        return r.getTurma() != null
                ? r.getTurma().getNomeTurma() + " · " + texto
                : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static TipoAviso tipoDaConsequencia(ConsequenciaDisciplinar consequencia) {
        return switch (consequencia) {
            case RECOMENDA_ADVERTENCIA -> TipoAviso.RECOMENDACAO_ADVERTENCIA;
            case RECOMENDA_SUSPENSAO -> TipoAviso.RECOMENDACAO_SUSPENSAO;
            case PERDE_PROXIMO_PASSEIO -> TipoAviso.PERDE_PROXIMO_PASSEIO;
            case RETORNO_COM_RESPONSAVEL -> TipoAviso.RETORNO_COM_RESPONSAVEL;
        };
    }

    private static String tituloDaConsequencia(ConsequenciaDisciplinar consequencia, String nome) {
        return switch (consequencia) {
            case RECOMENDA_ADVERTENCIA -> "Recomendação de advertência — " + nome;
            case RECOMENDA_SUSPENSAO -> "Recomendação de suspensão — " + nome;
            case PERDE_PROXIMO_PASSEIO -> nome + " perde o próximo passeio";
            case RETORNO_COM_RESPONSAVEL -> nome + " volta com o responsável";
        };
    }

    /** Por que a consequência veio: o 3º registro ativo do nível (NivelDisciplinar.LIMITE_PROGRESSAO). */
    private static String motivoDaConsequencia(ConsequenciaDisciplinar consequencia) {
        int limite = NivelDisciplinar.LIMITE_PROGRESSAO;
        return switch (consequencia) {
            case RECOMENDA_ADVERTENCIA -> limite + " ocorrências ativas";
            case RECOMENDA_SUSPENSAO -> limite + " advertências ativas";
            case PERDE_PROXIMO_PASSEIO -> limite + " suspensões ativas";
            case RETORNO_COM_RESPONSAVEL -> limite + " registros de uniforme";
        };
    }

    private static String rotuloDoNivel(NivelDisciplinar nivel) {
        return switch (nivel) {
            case OCORRENCIA -> "Ocorrência";
            case ADVERTENCIA -> "Advertência";
            case SUSPENSAO -> "Suspensão";
            case UNIFORME -> "Uniforme";
        };
    }

    private List<AvisoDTO> chamadasAtrasadas(LocalDate hoje) {
        return statusChamadaService.pendentes(hoje.minusDays(PainelService.DIAS_PENDENCIA), hoje.minusDays(1)).stream()
                .map(p -> new AvisoDTO(TipoAviso.CHAMADA_ATRASADA, p.nomeTurma() + " — " + p.data().format(DIA_MES),
                        "Chamada atrasada", null, p.turmaId(), p.data()))
                .toList();
    }

    private List<AvisoDTO> chamadasDeHoje(LocalDate hoje) {
        return chamadaService.chamadasDoDia(hoje).grupos().stream()
                .filter(g -> g.status() == StatusChamada.PENDENTE)
                .map(g -> new AvisoDTO(TipoAviso.CHAMADA_DE_HOJE, g.nomeTurma() + " — hoje",
                        "Chamada do dia ainda não lançada", null, g.turmaId(), hoje))
                .toList();
    }

    private List<AvisoDTO> faltasSeguidas(LocalDate hoje) {
        return painelRepository.faltasSeguidas(hoje.minusDays(PainelService.DIAS_FALTAS_SEGUIDAS), hoje,
                        IndicadorFrequencia.LIMIAR_FALTAS_CONSECUTIVAS).stream()
                .map(f -> new AvisoDTO(TipoAviso.FALTAS_SEGUIDAS, f.nome() + " — " + f.faltas() + " faltas seguidas",
                        (f.turma() != null ? f.turma() : "Sem grupo") + " · última chamada " + f.ultimaChamada().format(DIA_MES),
                        f.assistidoId(), null, f.ultimaChamada()))
                .toList();
    }

    /**
     * Semanário não enviado na semana, um aviso por oficina. A segunda fica para o oficineiro
     * enviar; de terça em diante, o que falta aparece para o Coordenador.
     */
    List<AvisoDTO> semanariosPendentes(LocalDate hoje) {
        if (hoje.getDayOfWeek() == DayOfWeek.MONDAY) {
            return List.of();
        }
        LocalDate segunda = hoje.with(DayOfWeek.MONDAY);
        return semanarioRepository.pendentesDaSemana(segunda).stream()
                .map(p -> new AvisoDTO(TipoAviso.SEMANARIO_PENDENTE, p.getNomeOficina() + " — semanário da semana",
                        (p.getGrupos() == 1 ? "1 grupo sem semanário" : p.getGrupos() + " grupos sem semanário")
                                + " na semana de " + segunda.format(DIA_MES),
                        null, null, segunda))
                .toList();
    }

    /** Pedidos de desligamento da Assistente Social esperando o Coordenador. */
    private List<AvisoDTO> desligamentosPendentes() {
        long pendentes = solicitacaoDesligamentoRepository.countByStatus(StatusSolicitacaoDesligamento.PENDENTE);
        return pendentes == 0 ? List.of()
                : List.of(new AvisoDTO(TipoAviso.DESLIGAMENTO_SOLICITADO,
                        pendentes + " pedido(s) de desligamento aguardando", "Solicitados pela Assistente Social",
                        null, null, null));
    }

    private List<AvisoDTO> cadastro() {
        List<AvisoDTO> avisos = new ArrayList<>();
        long preCadastros = preCadastroRepository.countByStatus(StatusPreCadastro.PENDENTE);
        if (preCadastros > 0) {
            avisos.add(new AvisoDTO(TipoAviso.PRE_CADASTRO_PENDENTE, preCadastros + " pré-cadastro(s) aguardando análise",
                    "Enviados pelo link público", null, null, null));
        }
        long semNascimento = assistidoRepository.countByDataNascimentoIsNullAndStatus(StatusGeral.ATIVO);
        if (semNascimento > 0) {
            avisos.add(new AvisoDTO(TipoAviso.CADASTRO_SEM_NASCIMENTO, semNascimento + " atendido(s) sem data de nascimento",
                    "Cadastro incompleto", null, null, null));
        }
        return avisos;
    }
}
