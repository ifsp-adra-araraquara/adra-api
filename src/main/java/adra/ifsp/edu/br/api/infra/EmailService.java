package adra.ifsp.edu.br.api.infra;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * O template e' o mesmo que o Supabase usa no "esqueci a senha"; por isso so'
 * conhece as variaveis dele, {{ .SiteURL }} e {{ .TokenHash }}.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final Resource templateRedefinicao;
    private final String remetenteEmail;
    private final String remetenteNome;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("classpath:templates/recovery.html") Resource templateRedefinicao,
            @Value("${adra.email.remetente-email}") String remetenteEmail,
            @Value("${adra.email.remetente-nome}") String remetenteNome,
            @Value("${adra.frontend.url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.templateRedefinicao = templateRedefinicao;
        this.remetenteEmail = remetenteEmail;
        this.remetenteNome = remetenteNome;
        this.frontendUrl = frontendUrl;
    }

    public String linkRedefinicaoSenha(String tokenHash) {
        return frontendUrl + "/redefinir-senha?token_hash=" + tokenHash + "&type=recovery";
    }

    public void enviarRedefinicaoSenha(String email, String tokenHash) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, StandardCharsets.UTF_8.name());
            helper.setFrom(remetenteEmail, remetenteNome);
            helper.setTo(email);
            helper.setSubject("Redefinição de senha — ADRA Araraquara");
            helper.setText(montarHtmlRedefinicao(tokenHash), true);
            mailSender.send(mensagem);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Nao foi possivel montar o e-mail de redefinicao de senha.", e);
        }
    }

    String montarHtmlRedefinicao(String tokenHash) {
        try {
            String template = StreamUtils.copyToString(templateRedefinicao.getInputStream(), StandardCharsets.UTF_8);
            return template
                    .replace("{{ .SiteURL }}", frontendUrl)
                    .replace("{{ .TokenHash }}", tokenHash);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel carregar o template de redefinicao de senha.", e);
        }
    }
}
