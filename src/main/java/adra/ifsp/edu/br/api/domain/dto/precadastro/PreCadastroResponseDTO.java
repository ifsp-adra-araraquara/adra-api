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
        String observacao,
        Long assistidoId,
        /** Ano letivo da inscrição. */
        Integer anoReferencia,
        /** Atendido que já tem este CPF (null se não há): a equipe rematricula em vez de aprovar. */
        Long assistidoExistenteId,
        /** O atendido existente já tem matrícula no ano da inscrição (rematrícula já feita). */
        boolean jaMatriculadoNoAno,
        /** Foto e documentos enviados pela família (só enquanto não foi aprovado: depois vão para o atendido). */
        List<ArquivoResponseDTO> arquivos,
        /** O que a família não enviou ("Foto da criança"...), para a equipe pedir na entrevista. */
        List<String> anexosFaltando,
        String revisadoPorNome,
        OffsetDateTime revisadoEm,
        OffsetDateTime criadoEm,
        /**
         * Nenhum atendido com este CPF, mas há um com o mesmo nome e data de nascimento
         * (ficha sem CPF ou com outro): provável rematrícula — a equipe confere antes de criar outra ficha.
         */
        PossivelMesmoAtendido possivelMesmoAtendido,
        /** Para qual ano a inscrição em avaliação pode ser movida (o seguinte ou de volta ao atual); null se não pode. */
        Integer anoParaMover
) {
    public record PossivelMesmoAtendido(Long assistidoId, String nomeCompleto, boolean fichaSemCpf) {
    }

    public static PreCadastroResponseDTO de(PreCadastro p, Long assistidoExistenteId, boolean jaMatriculadoNoAno,
                                            List<ArquivoResponseDTO> arquivos, List<String> anexosFaltando) {
        return de(p, assistidoExistenteId, jaMatriculadoNoAno, arquivos, anexosFaltando, null, null);
    }

    public static PreCadastroResponseDTO de(PreCadastro p, Long assistidoExistenteId, boolean jaMatriculadoNoAno,
                                            List<ArquivoResponseDTO> arquivos, List<String> anexosFaltando,
                                            PossivelMesmoAtendido possivelMesmoAtendido, Integer anoParaMover) {
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
                p.getObservacao(),
                p.getAssistidoId(),
                p.getAnoReferencia(),
                assistidoExistenteId,
                jaMatriculadoNoAno,
                arquivos,
                anexosFaltando,
                p.getRevisadoPor() != null ? p.getRevisadoPor().getNomeCompleto() : null,
                p.getRevisadoEm(),
                p.getCriadoEm(),
                possivelMesmoAtendido,
                anoParaMover);
    }
}
