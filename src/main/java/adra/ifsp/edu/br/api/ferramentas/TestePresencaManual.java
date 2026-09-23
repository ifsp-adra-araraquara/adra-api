package adra.ifsp.edu.br.api.ferramentas;

import adra.ifsp.edu.br.api.ApiApplication;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.domain.service.PresencaService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * NÃO é um teste JUnit — é um main() pra rodar uma vez na IDE (dá play
 * direto no método main, não precisa gradle bootRun nem mexer no
 * build.gradle) e ver no console se o fluxo de chamada tá funcionando de
 * ponta a ponta contra o banco de verdade que `make run` já usa.
 *
 * O QUE ELE FAZ:
 *  1. Acha uma Aula REALIZADA existente (com pelo menos 3 assistidos ativos
 *     na turma dela) e um Usuario qualquer pra simular o login.
 *  2. Se TurmaAlunos ainda não tiver vínculo pra essa turma, cria um de teste
 *     a partir de Assistido.turma (ver aviso importante no final do console).
 *  3. Lança uma chamada com 1 PRESENTE, 1 FALTA e 1 FALTA_JUSTIFICADA.
 *  4. Confere direto no banco que só as 2 faltas viraram linha em `presenca`
 *     (o presente não deveria ter linha nenhuma).
 *  5. Chama buscarPorAula e mostra o que o front vai receber.
 *  6. Reenvia o que faltou como PRESENTE e confere que a linha suniu.
 *  7. Tenta lançar FALTA_JUSTIFICADA sem motivo e confere que é bloqueado (CA-65.2).
 *
 * PRÉ-REQUISITO: o Supabase local tem que estar de pé (`make up`, igual pro
 * `make run` normal) — esse teste sobe com o profile "local", mesmo banco.
 *
 * IMPORTANTE: isso grava de verdade no banco local — não roda contra stage/
 * produção. É um arquivo de uso único, pode apagar depois de confirmar que
 * funciona (ou mover pra src/test/java se quiser manter).
 */
public class TestePresencaManual {

    public static void main(String[] args) {
        // Passado como argumento de linha de comando (não como .properties(...),
        // que vira "default property" e perde pro spring.profiles.active=stage
        // fixo no application.yml) — mesma precedência que o --args= do make run.
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ApiApplication.class)
                .run("--spring.profiles.active=local", "--server.port=0");

        try {
            PlatformTransactionManager txManager = ctx.getBean(PlatformTransactionManager.class);
            new TransactionTemplate(txManager).executeWithoutResult(status -> executar(ctx));
        } finally {
            ctx.close();
        }
    }

    private static void executar(ConfigurableApplicationContext ctx) {
        AulaRepository aulaRepository = ctx.getBean(AulaRepository.class);
        AssistidoRepository assistidoRepository = ctx.getBean(AssistidoRepository.class);
        UsuarioRepository usuarioRepository = ctx.getBean(UsuarioRepository.class);
        PresencaRepository presencaRepository = ctx.getBean(PresencaRepository.class);
        TurmaAlunosRepository turmaAlunosRepository = ctx.getBean(TurmaAlunosRepository.class);
        PresencaService presencaService = ctx.getBean(PresencaService.class);

        // --- pré-condições ---
        Aula aula = aulaRepository.findAll().stream()
                .filter(a -> a.getStatusAula() == StatusAula.REALIZADA)
                .findFirst()
                .orElse(null);
        if (aula == null) {
            System.out.println("PAROU: não achei nenhuma Aula com status REALIZADA. Cria uma (ou muda o status de uma existente) antes de rodar esse teste.");
            return;
        }

        Usuario usuarioLogado = usuarioRepository.findAll().stream().findFirst().orElse(null);
        if (usuarioLogado == null) {
            System.out.println("PAROU: não achei nenhum Usuario cadastrado pra simular o login.");
            return;
        }

        List<Assistido> assistidosDaTurma = assistidoRepository.findAll().stream()
                .filter(a -> a.getTurma() != null
                        && a.getTurma().getTurmaId().equals(aula.getTurma().getTurmaId())
                        && a.getStatus() == StatusGeral.ATIVO)
                .toList();
        if (assistidosDaTurma.size() < 3) {
            System.out.println("PAROU: preciso de pelo menos 3 assistidos ativos na turma \""
                    + aula.getTurma().getNomeTurma() + "\" (achei " + assistidosDaTurma.size() + ").");
            return;
        }

        System.out.println("Usando aula id=" + aula.getAulaId() + " (" + aula.getDataAula() + ") da turma \""
                + aula.getTurma().getNomeTurma() + "\", logado como usuário id=" + usuarioLogado.getUsuarioId());

        autenticarComo(usuarioLogado);

        // buscarPorAula depende de TurmaAlunos pra montar o roster — se estiver vazia
        // pra essa turma, cria vínculo de teste a partir de Assistido.turma.
        boolean turmaAlunosVazia = turmaAlunosRepository.findVinculosAtivosNaData(
                aula.getTurma(), StatusGeral.ATIVO, aula.getDataAula()).isEmpty();
        if (turmaAlunosVazia) {
            System.out.println("TurmaAlunos está vazia pra essa turma — criando vínculos de teste a partir de Assistido.turma...");
            for (Assistido assistido : assistidosDaTurma) {
                TurmaAlunos vinculo = new TurmaAlunos();
                vinculo.setTurma(aula.getTurma());
                vinculo.setAssistido(assistido);
                vinculo.setDataEntrada(assistido.getDataEntrada() != null ? assistido.getDataEntrada() : LocalDate.now().minusMonths(1));
                vinculo.setStatus(StatusGeral.ATIVO);
                turmaAlunosRepository.save(vinculo);
            }
        }

        Assistido presente = assistidosDaTurma.get(0);
        Assistido faltou = assistidosDaTurma.get(1);
        Assistido faltouJustificado = assistidosDaTurma.get(2);

        List<PresencaRequestDTO> lote = List.of(
                new PresencaRequestDTO(aula.getAulaId(), presente.getAssistidoId(), StatusPresenca.PRESENTE, null, null),
                new PresencaRequestDTO(aula.getAulaId(), faltou.getAssistidoId(), StatusPresenca.FALTA, null, null),
                new PresencaRequestDTO(aula.getAulaId(), faltouJustificado.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.OUTRO, "Consulta médica")
        );

        System.out.println("\n--- registrando chamada ---");
        List<PresencaResponseDTO> resposta = presencaService.registrarChamada(aula.getAulaId(), lote);
        resposta.forEach(r -> System.out.println("  " + r.assistidoId() + " -> " + r.statusPresenca()
                + (r.motivoFalta() != null ? " (" + r.motivoFalta() + ": " + r.observacao() + ")" : "")));

        System.out.println("\n--- checando o banco direto (só falta/falta_justificada deveriam ter linha) ---");
        List<Presenca> linhasNoBanco = presencaRepository.findByAula(aula);
        System.out.println("  linhas em presenca pra essa aula: " + linhasNoBanco.size() + " (esperado: 2 — presente não devia ter virado linha)");
        linhasNoBanco.forEach(p -> System.out.println("  presencaId=" + p.getPresencaId()
                + " assistidoId=" + p.getAssistido().getAssistidoId() + " status=" + p.getStatusPresenca()));

        System.out.println("\n--- buscarPorAula (o que o front vai receber) ---");
        List<PresencaResponseDTO> chamadaCompleta = presencaService.buscarPorAula(aula.getAulaId());
        System.out.println("  total no roster: " + chamadaCompleta.size());
        chamadaCompleta.forEach(r -> System.out.println("  assistidoId=" + r.assistidoId()
                + " status=" + r.statusPresenca() + (r.presencaId() == null ? "  (inferido, sem linha)" : "  (presencaId=" + r.presencaId() + ")")));

        System.out.println("\n--- testando reenvio: quem faltou agora vira PRESENTE ---");
        presencaService.registrarChamada(aula.getAulaId(), List.of(
                new PresencaRequestDTO(aula.getAulaId(), faltou.getAssistidoId(), StatusPresenca.PRESENTE, null, null)
        ));
        long linhasDepoisDoReenvio = presencaRepository.findByAula(aula).size();
        System.out.println("  linhas em presenca depois do reenvio: " + linhasDepoisDoReenvio + " (esperado: 1 — a linha de quem virou presente devia ter sumido)");

        System.out.println("\n--- testando CA-65.2: FALTA_JUSTIFICADA sem motivo devia ser bloqueada ---");
        try {
            presencaService.registrarChamada(aula.getAulaId(), List.of(
                    new PresencaRequestDTO(aula.getAulaId(), presente.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, null, null)
            ));
            System.out.println("  FALHOU: deveria ter lançado RegraNegocioException e não lançou.");
        } catch (RuntimeException e) {
            System.out.println("  OK, bloqueou como esperado: " + e.getMessage());
        }

        System.out.println("\nFIM DO TESTE.");
    }

    /** Simula o principal que o Spring Security colocaria no contexto a partir do JWT da própria API. */
    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste")
                .header("alg", "none")
                .subject(usuario.getUsuarioId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("perfil", usuario.getNivelPermissao().getNome().name())
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name())));

        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
