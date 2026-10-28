package adra.ifsp.edu.br.api.domain.dto.responsavel;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ResponsavelResponseDTO(
        Long responsavelId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String cpf,
        String telefone,
        String email,
        EnderecoDTO endereco,
        String observacoes,
        Escolaridade escolaridade,
        EstadoCivil estadoCivil,
        String profissao,
        BigDecimal renda,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        // Assistidos vinculados (total e quantos estão ativos). Null onde a
        // contagem não foi pedida (ex.: resposta de cadastro/edição).
        Integer totalAssistidos,
        Integer assistidosAtivos
) {
}
