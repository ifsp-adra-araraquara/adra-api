package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.model.Endereco;
import org.springframework.util.StringUtils;

/** Conversão do endereço embutido. Strings vazias viram null; endereço todo vazio vira null. */
public final class EnderecoMapper {

    private EnderecoMapper() {
    }

    public static Endereco paraEntidade(EnderecoDTO dto) {
        if (dto == null) {
            return null;
        }
        Endereco endereco = Endereco.builder()
                .logradouro(textoOuNulo(dto.logradouro()))
                .numero(textoOuNulo(dto.numero()))
                .bairro(textoOuNulo(dto.bairro()))
                .pontoReferencia(textoOuNulo(dto.pontoReferencia()))
                .build();
        return vazio(endereco) ? null : endereco;
    }

    public static EnderecoDTO paraDTO(Endereco entidade) {
        if (entidade == null || vazio(entidade)) {
            return null;
        }
        return new EnderecoDTO(
                entidade.getLogradouro(),
                entidade.getNumero(),
                entidade.getBairro(),
                entidade.getPontoReferencia()
        );
    }

    private static boolean vazio(Endereco e) {
        return e.getLogradouro() == null && e.getNumero() == null
                && e.getBairro() == null && e.getPontoReferencia() == null;
    }

    private static String textoOuNulo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
