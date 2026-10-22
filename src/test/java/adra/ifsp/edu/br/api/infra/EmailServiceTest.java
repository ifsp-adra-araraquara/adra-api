package adra.ifsp.edu.br.api.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

    private final EmailService emailService = new EmailService(
            mock(JavaMailSender.class),
            new ClassPathResource("templates/recovery.html"),
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
}
