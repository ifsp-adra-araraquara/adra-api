package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record AssistidoRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        @Size(max = 180, message = "Nome completo deve ter no maximo 180 caracteres")
        String nomeCompleto,

        @NotNull(message = "Data de nascimento e' obrigatoria")
        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        // CPF opcional e unico quando informado.
        @ValidCpf(message = "CPF invalido")
        String cpf,

        // Se nao informado, o service assume LocalDate.now().
        LocalDate dataEntrada,

        String necessidadesEspecificas,

        String observacoes,

        Long turmaId,

        Sexo sexo,

        // Saude: a descricao e' obrigatoria quando o indicador vier true
        // (validado no AssistidoService).
        Boolean possuiProblemaSaude,

        String problemaSaude,

        Boolean pcd,

        String deficiencia,

        @Size(max = 180, message = "Nome da escola deve ter no maximo 180 caracteres")
        String escolaNome,

        @Size(max = 40, message = "Ano/serie deve ter no maximo 40 caracteres")
        String escolaAnoSerie,

        Turno escolaTurno,

        Boolean estudaContraturno,

        @Valid
        EnderecoDTO endereco,

        // ---- Socioeconomico (familia) ----
        @PositiveOrZero(message = "Renda familiar nao pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "Renda familiar invalida")
        BigDecimal rendaFamiliar,

        // true exige ao menos um programa; OUTRO exige a descricao (AssistidoService).
        Boolean recebeProgramaTransferencia,

        Set<ProgramaTransferencia> programasTransferencia,

        @Size(max = 120, message = "Programa (outro) deve ter no maximo 120 caracteres")
        String programaTransferenciaOutro,

        @Pattern(regexp = "^$|\\d{11}", message = "NIS/CadUnico deve ter 11 digitos")
        String nis,

        // ---- Termos e autorizacoes (null na edicao = mantem o valor atual) ----
        // Aceite LGPD e' obrigatorio no cadastro pelo formulario (cadastrarComArquivos).
        Boolean aceiteLgpd,

        Boolean autorizaUsoImagem,

        Boolean autorizaEventosPasseios,

        Boolean podeIrEmboraSozinho,

        Boolean cienteFrequenciaObrigatoria,

        // Responsaveis NOVOS a cadastrar e vincular nesta chamada (cadastro
        // inicial do assistido, ou novos responsaveis incluidos numa edicao).
        @Valid
        List<VinculoFamiliarComResponsavelRequestDTO> responsaveis,

        // CA-A04: responsaveis JA vinculados que devem ser mantidos (com os
        // dados do vinculo atualizados). So usado na edicao - null/ausente
        // significa "esta chamada nao gerencia responsaveis existentes".
        // Um vinculo existente que NAO aparecer aqui e' desvinculado.
        @Valid
        List<VinculoFamiliarRequestDTO> responsaveisVinculados
) {
}
