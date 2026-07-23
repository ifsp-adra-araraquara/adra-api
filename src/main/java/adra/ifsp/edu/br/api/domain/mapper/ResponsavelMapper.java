package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import org.springframework.stereotype.Component;

@Component
public class ResponsavelMapper {

    public Responsavel paraNovaEntidade(ResponsavelRequestDTO dto) {
        return Responsavel.builder()
                .nomeCompleto(dto.nomeCompleto())
                .dataNascimento(dto.dataNascimento())
                .cpf(dto.cpf())
                .telefone(dto.telefone())
                .email(dto.email())
                .endereco(dto.endereco())
                .observacoes(dto.observacoes())
                .build();
    }

    public void atualizarEntidade(Responsavel entidade, ResponsavelRequestDTO dto) {
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setDataNascimento(dto.dataNascimento());
        entidade.setCpf(dto.cpf());
        entidade.setTelefone(dto.telefone());
        entidade.setEmail(dto.email());
        entidade.setEndereco(dto.endereco());
        entidade.setObservacoes(dto.observacoes());
    }

    public ResponsavelResponseDTO paraDTO(Responsavel entidade) {
        return new ResponsavelResponseDTO(
                entidade.getResponsavelId(),
                entidade.getNomeCompleto(),
                entidade.getDataNascimento(),
                entidade.getCpf(),
                entidade.getTelefone(),
                entidade.getEmail(),
                entidade.getEndereco(),
                entidade.getObservacoes(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm()
        );
    }
}
