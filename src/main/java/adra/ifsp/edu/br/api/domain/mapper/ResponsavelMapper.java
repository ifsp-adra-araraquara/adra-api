package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ResponsavelMapper {

    public Responsavel paraNovaEntidade(ResponsavelRequestDTO dto) {
        return Responsavel.builder()
                .nomeCompleto(dto.nomeCompleto())
                .dataNascimento(dto.dataNascimento())
                .cpf(dto.cpf())
                .telefone(dto.telefone())
                .email(dto.email())
                .endereco(EnderecoMapper.paraEntidade(dto.endereco()))
                .observacoes(dto.observacoes())
                .escolaridade(dto.escolaridade())
                .estadoCivil(dto.estadoCivil())
                .profissao(textoOuNulo(dto.profissao()))
                .renda(dto.renda())
                .build();
    }

    public void atualizarEntidade(Responsavel entidade, ResponsavelRequestDTO dto) {
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setDataNascimento(dto.dataNascimento());
        entidade.setCpf(dto.cpf());
        entidade.setTelefone(dto.telefone());
        entidade.setEmail(dto.email());
        entidade.setEndereco(EnderecoMapper.paraEntidade(dto.endereco()));
        entidade.setObservacoes(dto.observacoes());
        entidade.setEscolaridade(dto.escolaridade());
        entidade.setEstadoCivil(dto.estadoCivil());
        entidade.setProfissao(textoOuNulo(dto.profissao()));
        entidade.setRenda(dto.renda());
    }

    public ResponsavelResponseDTO paraDTO(Responsavel entidade) {
        return new ResponsavelResponseDTO(
                entidade.getResponsavelId(),
                entidade.getNomeCompleto(),
                entidade.getDataNascimento(),
                entidade.getCpf(),
                entidade.getTelefone(),
                entidade.getEmail(),
                EnderecoMapper.paraDTO(entidade.getEndereco()),
                entidade.getObservacoes(),
                entidade.getEscolaridade(),
                entidade.getEstadoCivil(),
                entidade.getProfissao(),
                entidade.getRenda(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm()
        );
    }

    private String textoOuNulo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
