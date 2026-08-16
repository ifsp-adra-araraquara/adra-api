package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public Usuario paraNovaEntidade(UsuarioRequestDTO dto, NivelPermissao nivelPermissao) {
        return Usuario.builder()
                .nivelPermissao(nivelPermissao)
                .nomeCompleto(dto.nomeCompleto())
                .email(dto.email())
                .cargoFuncao(dto.cargoFuncao())
                .telefone(dto.telefone())
                .especialidade(dto.especialidade())
                .ativo(true)
                .build();
    }

    public void atualizarEntidade(Usuario entidade, UsuarioRequestDTO dto, NivelPermissao nivelPermissao) {
        entidade.setNivelPermissao(nivelPermissao);
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setEmail(dto.email());
        entidade.setCargoFuncao(dto.cargoFuncao());
        entidade.setTelefone(dto.telefone());
        entidade.setEspecialidade(dto.especialidade());
    }

    public UsuarioResponseDTO paraDTO(Usuario entidade) {
        return new UsuarioResponseDTO(
                entidade.getUsuarioId(),
                entidade.getNomeCompleto(),
                entidade.getEmail(),
                entidade.getNivelPermissao().getNome(),
                entidade.getEspecialidade(),
                entidade.getCargoFuncao(),
                entidade.getTelefone(),
                entidade.isAtivo(),
                entidade.getUltimoLogin(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm()
        );
    }
}
