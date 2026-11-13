package adra.ifsp.edu.br.api.infra;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * O template e' o mesmo que o Supabase usa no "esqueci a senha"; por isso so'
 * conhece as variaveis dele, {{ .SiteURL }} e {{ .TokenHash }}.
 */
@Service
public class EmailService {

    static final int MAXIMO_ITENS_RESUMO = 30;

    private final JavaMailSender mailSender;
    private final Resource templateRedefinicao;
    private final Resource templateResumo;
    private final String remetenteEmail;
    private final String remetenteNome;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("classpath:templates/recovery.html") Resource templateRedefinicao,
            @Value("classpath:templates/notificacoes.html") Resource templateResumo,
            @Value("${adra.email.remetente-email}") String remetenteEmail,
            @Value("${adra.email.remetente-nome}") String remetenteNome,
            @Value("${adra.frontend.url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.templateRedefinicao = templateRedefinicao;
        this.templateResumo = templateResumo;
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

    /** Uma linha do resumo de notificações; {@code link} é rota do front, ou null. */
    public record ItemResumo(String titulo, String mensagem, String link) {
    }

    /** FT-14: o resumo diário. Um e-mail só, com tudo o que ficou pendente para a pessoa. */
    public void enviarResumoNotificacoes(String email, String nome, List<ItemResumo> itens) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, StandardCharsets.UTF_8.name());
            helper.setFrom(remetenteEmail, remetenteNome);
            helper.setTo(email);
            helper.setSubject(assuntoResumo(itens.size()));
            helper.setText(montarHtmlResumo(nome, itens), true);
            mailSender.send(mensagem);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Nao foi possivel montar o resumo de notificacoes.", e);
        }
    }

    static String assuntoResumo(int total) {
        return (total == 1 ? "1 aviso" : total + " avisos") + " para você — ADRA Araraquara";
    }

    /** Cabem até {@value #MAXIMO_ITENS_RESUMO} itens; o resto fica no sistema, com um "e mais N". */
    String montarHtmlResumo(String nome, List<ItemResumo> itens) {
        StringBuilder linhas = new StringBuilder();
        itens.stream().limit(MAXIMO_ITENS_RESUMO).forEach(item -> linhas.append(linhaResumo(item)));
        if (itens.size() > MAXIMO_ITENS_RESUMO) {
            linhas.append("<tr><td style=\"padding:12px 0 0; font-size:13px; color:#54585A;\">E mais ")
                    .append(itens.size() - MAXIMO_ITENS_RESUMO)
                    .append(" no sistema.</td></tr>");
        }
        try {
            String template = StreamUtils.copyToString(templateResumo.getInputStream(), StandardCharsets.UTF_8);
            return template
                    .replace("{{ .Nome }}", escapar(primeiroNome(nome)))
                    .replace("{{ .Introducao }}", itens.size() == 1
                            ? "Tem 1 aviso que precisa da sua atenção no Sistema de Gestão."
                            : "Tem " + itens.size() + " avisos que precisam da sua atenção no Sistema de Gestão.")
                    .replace("{{ .Itens }}", linhas)
                    .replace("{{ .SiteURL }}", frontendUrl);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel carregar o template do resumo de notificacoes.", e);
        }
    }

    private String linhaResumo(ItemResumo item) {
        String titulo = escapar(item.titulo());
        if (item.link() != null) {
            titulo = "<a href=\"" + escapar(frontendUrl + item.link())
                    + "\" style=\"color:#2D2F30; text-decoration:none;\">" + titulo + "</a>";
        }
        String mensagem = item.mensagem() == null ? ""
                : "<br><span style=\"font-size:13px; color:#54585A;\">" + escapar(item.mensagem()) + "</span>";
        return "<tr><td style=\"padding:12px 0; border-bottom:1px solid #EDEEEE; font-size:14px; line-height:1.5;"
                + " color:#2D2F30;\"><strong>" + titulo + "</strong>" + mensagem + "</td></tr>";
    }

    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
    }

    private static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        int espaco = limpo.indexOf(' ');
        return espaco > 0 ? limpo.substring(0, espaco) : limpo;
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
