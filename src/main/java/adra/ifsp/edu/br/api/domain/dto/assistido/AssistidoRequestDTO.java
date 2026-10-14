package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

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
