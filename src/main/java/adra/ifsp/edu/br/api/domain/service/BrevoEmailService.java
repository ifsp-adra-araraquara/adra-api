package adra.ifsp.edu.br.api.infra;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrevoEmailService {
    
    public void enviarConvite(String email, String token) {
        // TODO: Integração com Brevo (vem de US-03)
        //apenas para o código compilar e funcionar durante o desenvolvimento, mas não envia e-mail de verdade ainda.
        System.out.println("Email de convite seria enviado para: " + email);
        System.out.println("Token: " + token);
    }
}