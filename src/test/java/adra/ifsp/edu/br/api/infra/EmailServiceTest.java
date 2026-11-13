package adra.ifsp.edu.br.api.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

    private final EmailService emailService = new EmailService(
            mock(JavaMailSender.class),
            new ClassPathResource("templates/recovery.html"),
            new ClassPathResource("templates/notificacoes.html"),
            "naoresponda@adra.test",
            "ADRA Teste",
            "https://front.teste");

    @Test
    void templateDoSupabaseRecebeOMesmoLinkQueOAdministradorCopia() {
        String html = emailService.montarHtmlRedefinicao("token-teste");

        assertThat(html)
                .contains(emailService.linkRedefinicaoSenha("token-teste"))
                .doesNotContain("{{");
    }

    @Test
    void resumoTrazCadaAvisoComLinkParaOSistemaEEscapaOTexto() {
        String html = emailService.montarHtmlResumo("Maria Coordenadora", List.of(
                new EmailService.ItemResumo("Ana <b>Silva</b> chegou a 3 faltas seguidas", "SI Manhã · última chamada em 10/11.",
                        "/assistidos?assistidoId=12&aba=cham"),
                new EmailService.ItemResumo("Semanário atrasado — Futebol", null, null)));

        assertThat(html)
                .contains("Olá, Maria")
                .contains("Tem 2 avisos")
                .contains("Ana &lt;b&gt;Silva&lt;/b&gt; chegou a 3 faltas seguidas")
                .contains("https://front.teste/assistidos?assistidoId=12&amp;aba=cham")
                .contains("Semanário atrasado — Futebol")
                .contains("https://front.teste/notificacoes")
                .doesNotContain("{{");
    }

    @Test
    void resumoGrandeMostraOsPrimeirosEApontaOResto() {
        List<EmailService.ItemResumo> itens = IntStream.rangeClosed(1, EmailService.MAXIMO_ITENS_RESUMO + 5)
                .mapToObj(i -> new EmailService.ItemResumo("Aviso " + i, null, null))
                .toList();

        assertThat(emailService.montarHtmlResumo("Maria", itens))
                .contains("Aviso " + EmailService.MAXIMO_ITENS_RESUMO + "<")
                .doesNotContain("Aviso " + (EmailService.MAXIMO_ITENS_RESUMO + 1) + "<")
                .contains("E mais 5 no sistema.");
    }

    @Test
    void assuntoNoSingularENoPlural() {
        assertThat(EmailService.assuntoResumo(1)).startsWith("1 aviso para você");
        assertThat(EmailService.assuntoResumo(3)).startsWith("3 avisos para você");
    }
}
