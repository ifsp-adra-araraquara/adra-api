package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;

public interface UsuarioAutenticadoService {
    Usuario getUsuarioAtual();

    boolean temPerfil(NomeNivelPermissao perfil);

}
