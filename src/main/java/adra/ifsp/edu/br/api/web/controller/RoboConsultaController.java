package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.DisciplinarResumoRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.DistribuicaoAtendidosDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FaltasSeguidasRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaDiaSemanaDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.MovimentoAtendidosDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.SocialResumoDTO;
import adra.ifsp.edu.br.api.domain.enums.DimensaoAtendidos;
import adra.ifsp.edu.br.api.domain.service.AcessoTurmaService;
import adra.ifsp.edu.br.api.domain.service.RoboConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Robozinho ADRA — consultas só de leitura que respondem às perguntas do dia a
 * dia. Tudo chega agregado; o robô (front) só formata, compara e imprime.
 *
 * <p>Permissão por método, espelhando as telas equivalentes: frequência para
 * quem faz/consulta chamada (o Oficineiro só vê os grupos das oficinas dele),
 * atendidos e perfil social para Coordenação e Assistente Social.
 * Não ponha @PreAuthorize na classe.
 */
@RestController
@RequestMapping("/api/robo")
@RequiredArgsConstructor
public class RoboConsultaController {

    private final RoboConsultaService service;
    private final AcessoTurmaService acessoTurma;

    /** Grupos e oficinas que a pessoa pode ver (id e nome), para o robô reconhecer nomes. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/contexto")
    public ResponseEntity<RoboContextoDTO> contexto() {
        return privado(service.contexto(escopo()));
    }

    /** Frequência por mês e grupo; sem `de`/`ate`, o mês atual. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/frequencia")
    public ResponseEntity<FrequenciaRoboDTO> frequencia(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth de,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth ate
    ) {
        YearMonth fim = ate != null ? ate : YearMonth.now();
        return privado(service.frequencia(de != null ? de : fim, fim, escopo()));
    }

    /** Em que dia da semana falta mais gente; `turmaId` opcional (o Oficineiro só nos grupos dele). */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL') "
            + "or (hasRole('OFICINEIRO') and (#turmaId == null or @acessoTurma.podeAcessarTurma(#turmaId)))")
    @GetMapping("/frequencia/dia-semana")
    public ResponseEntity<FrequenciaDiaSemanaDTO> frequenciaPorDiaDaSemana(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth de,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth ate,
            @RequestParam(required = false) Long turmaId
    ) {
        YearMonth fim = ate != null ? ate : YearMonth.now();
        return privado(service.frequenciaPorDiaDaSemana(de != null ? de : fim.minusMonths(2), fim, turmaId, escopo()));
    }

    /** Quem está faltando seguido (risco de evasão). */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/frequencia/faltas-seguidas")
    public ResponseEntity<FaltasSeguidasRoboDTO> faltasSeguidas(@RequestParam(required = false) Integer minimo) {
        return privado(service.faltasSeguidas(minimo, escopo()));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/atendidos/distribuicao")
    public ResponseEntity<DistribuicaoAtendidosDTO> distribuicao(@RequestParam DimensaoAtendidos por) {
        return privado(service.distribuicao(por));
    }

    /** Entradas, saídas e motivos de um ano; sem `ano`, o atual. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/atendidos/movimento")
    public ResponseEntity<MovimentoAtendidosDTO> movimento(@RequestParam(required = false) Integer ano) {
        return privado(service.movimento(ano != null ? ano : LocalDate.now().getYear()));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/disciplinar/resumo")
    public ResponseEntity<DisciplinarResumoRoboDTO> disciplinar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes
    ) {
        return privado(service.disciplinar(mes != null ? mes : YearMonth.now()));
    }

    /** Atendidos ativos pelo nome (até 8), para "frequência do João"; o Oficineiro só acha os dos grupos dele. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/atendidos/busca")
    public ResponseEntity<List<AtendidoRoboDTO>> buscarAtendidos(@RequestParam String nome) {
        return privado(service.buscarAtendidos(nome, escopo()));
    }

    /** Aniversariantes do mês (1–12; sem `mes`, o atual). */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/atendidos/aniversariantes")
    public ResponseEntity<List<AtendidoRoboDTO>> aniversariantes(@RequestParam(required = false) Integer mes) {
        return privado(service.aniversariantes(mes != null ? mes : LocalDate.now().getMonthValue(), escopo()));
    }

    /** Quem está a um registro da próxima medida disciplinar. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/disciplinar/um-passo")
    public ResponseEntity<List<AtendidoRoboDTO>> umPasso() {
        return privado(service.umPassoDaProximaMedida());
    }

    /** Perfil social (só contagens) para prestação de contas. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/social/resumo")
    public ResponseEntity<SocialResumoDTO> social() {
        return privado(service.social());
    }

    /** Id que não existe: Oficineiro sem id no token não vê grupo nenhum (nunca "todos"). */
    private static final long NINGUEM = -1L;

    /** Oficineiro: o próprio id restringe aos grupos dele; demais perfis: sem restrição. */
    private Long escopo() {
        if (!acessoTurma.usuarioAtualEhOficineiro()) return null;
        Long usuarioId = acessoTurma.usuarioIdAtual();
        return usuarioId != null ? usuarioId : NINGUEM;
    }

    private static <T> ResponseEntity<T> privado(T corpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).body(corpo);
    }
}
