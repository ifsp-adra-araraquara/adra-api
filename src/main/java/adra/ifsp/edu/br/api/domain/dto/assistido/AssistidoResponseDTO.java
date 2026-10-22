package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

public record AssistidoResponseDTO(
        Long assistidoId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String cpf,
        LocalDate dataEntrada,
        LocalDate dataSaida,
        String motivoSaida,
        String necessidadesEspecificas,
        String observacoes,
        Sexo sexo,
        Boolean possuiProblemaSaude,
        String problemaSaude,
        Boolean pcd,
        String deficiencia,
        String escolaNome,
        String escolaAnoSerie,
        Turno escolaTurno,
        Boolean estudaContraturno,
        EnderecoDTO endereco,
        BigDecimal rendaFamiliar,
        Boolean recebeProgramaTransferencia,
        Set<ProgramaTransferencia> programasTransferencia,
        String programaTransferenciaOutro,
        String nis,
        boolean aceiteLgpd,
        OffsetDateTime aceiteLgpdEm,
        boolean autorizaUsoImagem,
        boolean autorizaEventosPasseios,
        boolean podeIrEmboraSozinho,
        boolean cienteFrequenciaObrigatoria,
        StatusGeral status,
        Long turmaId,
        String nomeTurma,
        Integer totalOcorrenciasAtivas,
        Integer totalAdvertenciasAtivas,
        Integer totalSuspensoes,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        // Indicadores da listagem (GET /api/assistidos) — null nos demais endpoints.
        String responsavelNome,
        String responsavelParentesco,
        String responsavelTelefone,
        Integer frequenciaPercentual,
        Integer totalAulas,
        Integer faltasConsecutivas,
        Boolean emAcompanhamento,
        // Listagem filtrada por turma: período do assistido NESSA turma
        // (dataSaidaTurma null = ainda está). Null nos demais casos.
        LocalDate dataEntradaTurma,
        LocalDate dataSaidaTurma
) {

    /** Mesmo DTO com o período do assistido na turma filtrada. */
    public AssistidoResponseDTO comPeriodoNaTurma(LocalDate entrada, LocalDate saida) {
        return new AssistidoResponseDTO(assistidoId, nomeCompleto, dataNascimento, cpf, dataEntrada, dataSaida, motivoSaida, necessidadesEspecificas, observacoes, sexo, possuiProblemaSaude, problemaSaude, pcd, deficiencia, escolaNome, escolaAnoSerie, escolaTurno, estudaContraturno, endereco, rendaFamiliar, recebeProgramaTransferencia, programasTransferencia, programaTransferenciaOutro, nis, aceiteLgpd, aceiteLgpdEm, autorizaUsoImagem, autorizaEventosPasseios, podeIrEmboraSozinho, cienteFrequenciaObrigatoria, status, turmaId, nomeTurma, totalOcorrenciasAtivas, totalAdvertenciasAtivas, totalSuspensoes, criadoEm, atualizadoEm, responsavelNome, responsavelParentesco, responsavelTelefone, frequenciaPercentual, totalAulas, faltasConsecutivas, emAcompanhamento, entrada, saida);
    }

    /** Só id e nome: o que o oficineiro precisa, sem dado pessoal sensível (LGPD). */
    public AssistidoResponseDTO resumo() {
        return new AssistidoResponseDTO(assistidoId, nomeCompleto,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, null, false, false, false, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
