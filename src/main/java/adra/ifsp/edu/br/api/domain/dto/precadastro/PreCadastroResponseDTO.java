package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.dto.arquivo.ArquivoResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Detalhe do pré-cadastro: tudo o que a família enviou e a revisão da equipe. */
public record PreCadastroResponseDTO(
        Long preCadastroId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String cpf,
        Sexo sexo,
        String email,
        String responsavelNome,
        String responsavelTelefone,
        PreCadastroDadosDTO dados,
        StatusPreCadastro status,
        String motivoReprovacao,
        Long assistidoId,
        /** Atendido que já tem este CPF (null se não há): a equipe abre a ficha em vez de aprovar. */
        Long assistidoExistenteId,
        /** Foto e documentos enviados pela família (só enquanto não foi aprovado: depois vão para o atendido). */
        List<ArquivoResponseDTO> arquivos,
        /** O que a família não enviou ("Foto da criança"...), para a equipe pedir na entrevista. */
        List<String> anexosFaltando,
        String revisadoPorNome,
        OffsetDateTime revisadoEm,
        OffsetDateTime criadoEm
) {
    public static PreCadastroResponseDTO de(PreCadastro p, Long assistidoExistenteId,
                                            List<ArquivoResponseDTO> arquivos, List<String> anexosFaltando) {
        return new PreCadastroResponseDTO(
                p.getPreCadastroId(),
                p.getNomeCompleto(),
                p.getDataNascimento(),
                p.getCpf(),
                p.getSexo(),
                p.getEmail(),
                p.getResponsavelNome(),
                p.getResponsavelTelefone(),
                p.getDados(),
                p.getStatus(),
                p.getMotivoReprovacao(),
                p.getAssistidoId(),
                assistidoExistenteId,
                arquivos,
                anexosFaltando,
                p.getRevisadoPor() != null ? p.getRevisadoPor().getNomeCompleto() : null,
                p.getRevisadoEm(),
                p.getCriadoEm());
    }
}
