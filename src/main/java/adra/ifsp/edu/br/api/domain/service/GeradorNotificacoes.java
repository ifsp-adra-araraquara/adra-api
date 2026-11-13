package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.notificacao.PedidoNotificacao;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Notificacao.Envolvido;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.PainelRepository;
import adra.ifsp.edu.br.api.domain.repository.SemanarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * FT-14: transforma em notificação o que a central de avisos (FT-18) calcula e
 * merece e-mail. Por enquanto, o que saiu da reunião com a ADRA:
 * <ul>
 *   <li>atendido com 3 faltas seguidas → Coordenadores;</li>
 *   <li>semanário da semana não enviado → lembrete ao oficineiro e, de terça em
 *       diante, aviso de atraso aos Coordenadores (mesma regra do FT-18).</li>
 * </ul>
 * Momento novo = mais um método aqui chamando {@link NotificacaoService#notificar}.
 */
@Service
@RequiredArgsConstructor
public class GeradorNotificacoes {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final NotificacaoService notificacaoService;
    private final PainelRepository painelRepository;
    private final SemanarioRepository semanarioRepository;
    private final OficinaRepository oficinaRepository;

    /** @return quantas notificações novas foram criadas */
    @Transactional
    public int gerar(LocalDate hoje) {
        return faltasSeguidas(hoje) + semanariosPendentes(hoje);
    }

    /**
     * A referência leva o dia da primeira falta da sequência: a 4ª e a 5ª falta
     * da mesma sequência não avisam de novo; uma sequência nova, depois de uma
     * presença, avisa.
     */
    int faltasSeguidas(LocalDate hoje) {
        List<Usuario> coordenadores = notificacaoService.comPerfil(NomeNivelPermissao.COORDENADOR);
        if (coordenadores.isEmpty()) {
            return 0;
        }
        int novas = 0;
        for (PainelRepository.FaltasSeguidas f : painelRepository.faltasSeguidas(
                hoje.minusDays(PainelService.DIAS_FALTAS_SEGUIDAS), hoje, IndicadorFrequencia.LIMIAR_FALTAS_CONSECUTIVAS)) {
            novas += notificacaoService.notificar(new PedidoNotificacao(
                    TipoAviso.FALTAS_SEGUIDAS,
                    "assistido:" + f.assistidoId() + ":desde:" + f.primeiraFalta(),
                    f.nome() + " chegou a " + f.faltas() + " faltas seguidas",
                    (f.turma() != null ? f.turma() : "Sem grupo") + " · última chamada em "
                            + f.ultimaChamada().format(DIA_MES) + ". Vale um contato com a família.",
                    "/assistidos?assistidoId=" + f.assistidoId() + "&aba=cham",
                    Set.of(Envolvido.assistido(f.assistidoId())),
                    coordenadores,
                    PedidoNotificacao.TODOS_OS_CANAIS));
        }
        return novas;
    }

    /**
     * Um aviso por oficina e semana. O oficineiro recebe o lembrete já na
     * segunda; o Coordenador só de terça em diante. Se o oficineiro também é
     * Coordenador, recebe uma vez só (mesma referência).
     */
    int semanariosPendentes(LocalDate hoje) {
        LocalDate segunda = hoje.with(DayOfWeek.MONDAY);
        List<Usuario> coordenadores = hoje.getDayOfWeek() == DayOfWeek.MONDAY
                ? List.of()
                : notificacaoService.comPerfil(NomeNivelPermissao.COORDENADOR);
        int novas = 0;
        for (SemanarioRepository.SemanarioPendente p : semanarioRepository.pendentesDaSemana(segunda)) {
            Oficina oficina = oficinaRepository.findById(p.getOficinaId()).orElseThrow();
            Usuario oficineiro = oficina.getOficineiroResponsavel();
            String referencia = "oficina:" + p.getOficinaId() + ":semana:" + segunda;
            String grupos = p.getGrupos() == 1 ? "1 grupo" : p.getGrupos() + " grupos";
            Set<Envolvido> envolvidos = Set.of(Envolvido.oficina(p.getOficinaId()), Envolvido.usuario(oficineiro.getUsuarioId()));

            novas += notificacaoService.notificar(new PedidoNotificacao(
                    TipoAviso.SEMANARIO_PENDENTE, referencia,
                    "Lembrete: semanário de " + p.getNomeOficina(),
                    "Falta enviar o semanário de " + grupos + " na semana de " + segunda.format(DIA_MES) + ".",
                    "/oficineiro",
                    envolvidos, List.of(oficineiro), PedidoNotificacao.TODOS_OS_CANAIS));

            if (!coordenadores.isEmpty()) {
                novas += notificacaoService.notificar(new PedidoNotificacao(
                        TipoAviso.SEMANARIO_PENDENTE, referencia,
                        "Semanário atrasado — " + p.getNomeOficina(),
                        grupos + " sem semanário na semana de " + segunda.format(DIA_MES)
                                + " · oficineiro " + oficineiro.getNomeCompleto() + ".",
                        "/oficinas",
                        envolvidos, coordenadores, PedidoNotificacao.TODOS_OS_CANAIS));
            }
        }
        return novas;
    }
}
