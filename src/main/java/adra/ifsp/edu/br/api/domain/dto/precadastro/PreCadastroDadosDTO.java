package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * O que a família respondeu no formulário de inscrição, além das colunas de
 * {@code pre_cadastro} (nome, nascimento, CPF e sexo da criança). Guardado em
 * jsonb. Mãe, pai e responsável legal são opcionais: o bloco que vier precisa
 * estar completo. As regras que dependem de outra resposta ("se sim, qual?")
 * ficam no PreCadastroService.
 */
public record PreCadastroDadosDTO(
        @NotNull(message = "Responda os termos.")
        @Valid
        Termos termos,

        @Valid
        Crianca crianca,

        @Valid
        Mae mae,

        @Valid
        Pai pai,

        @Valid
        ResponsavelLegal responsavelLegal,

        @NotNull(message = "Informe o endereço da família.")
        @Valid
        EnderecoDTO enderecoFamilia,

        @NotNull(message = "Responda sobre programas de transferência de renda.")
        @Valid
        Transferencia transferencia,

        @NotNull(message = "Responda sobre a saúde da criança.")
        @Valid
        Saude saude,

        @NotNull(message = "Responda as autorizações.")
        @Valid
        Autorizacoes autorizacoes
) {

    private static final String TELEFONE = "\\d{10,11}";
    private static final String MENSAGEM_TELEFONE = "Telefone deve ter DDD + número (10 ou 11 dígitos).";

    public record Termos(
            @NotNull(message = "Responda a declaração da LGPD.")
            @AssertTrue(message = "Sem o aceite da LGPD não é possível enviar a inscrição.")
            Boolean aceiteLgpd,

            @NotNull(message = "Responda o termo de cessão de imagem.")
            Boolean autorizaUsoImagem
    ) {
    }

    public record Crianca(@Valid EnderecoDTO endereco) {
    }

    public record Mae(
            @NotBlank(message = "Informe o nome da mãe.")
            @Size(max = 180)
            String nomeCompleto,

            @NotNull(message = "Informe a data de nascimento da mãe.")
            @Past(message = "Data de nascimento da mãe deve estar no passado.")
            LocalDate dataNascimento,

            @NotBlank(message = "Informe o CPF da mãe.")
            @ValidCpf(message = "CPF da mãe inválido.")
            String cpf,

            @NotNull(message = "Informe a escolaridade da mãe.")
            Escolaridade escolaridade,

            EstadoCivil estadoCivil,

            @Size(max = 60)
            String estadoCivilOutro,

            @Size(max = 120)
            String profissao,

            @NotNull(message = "Informe a renda da mãe.")
            @PositiveOrZero(message = "Renda não pode ser negativa.")
            @Digits(integer = 10, fraction = 2, message = "Renda inválida.")
            BigDecimal renda,

            @NotBlank(message = "Informe o telefone da mãe.")
            @Pattern(regexp = TELEFONE, message = MENSAGEM_TELEFONE)
            String telefone
    ) {
    }

    public record Pai(
            @NotBlank(message = "Informe o nome do pai.")
            @Size(max = 180)
            String nomeCompleto,

            @ValidCpf(message = "CPF do pai inválido.")
            String cpf,

            @Size(max = 120)
            String profissao,

            @NotNull(message = "Informe a renda do pai.")
            @PositiveOrZero(message = "Renda não pode ser negativa.")
            @Digits(integer = 10, fraction = 2, message = "Renda inválida.")
            BigDecimal renda,

            @Pattern(regexp = "^$|" + TELEFONE, message = MENSAGEM_TELEFONE)
            String telefone
    ) {
    }

    public record ResponsavelLegal(
            @NotBlank(message = "Informe o nome do responsável legal.")
            @Size(max = 180)
            String nomeCompleto,

            @NotBlank(message = "Informe o parentesco do responsável legal.")
            @Size(max = 60)
            String parentesco,

            @ValidCpf(message = "CPF do responsável legal inválido.")
            String cpf,

            @NotBlank(message = "Informe o telefone do responsável legal.")
            @Pattern(regexp = TELEFONE, message = MENSAGEM_TELEFONE)
            String telefone
    ) {
    }

    public record Transferencia(
            @NotNull(message = "Responda se a família recebe programa de transferência de renda.")
            Boolean recebe,

            Set<ProgramaTransferencia> programas,

            @Size(max = 120)
            String outro,

            @Pattern(regexp = "^$|\\d{11}", message = "NIS deve ter 11 dígitos.")
            String nis
    ) {
    }

    public record Saude(
            @NotNull(message = "Responda se a criança tem algum problema de saúde.")
            Boolean possuiProblema,

            @Size(max = 1000)
            String descricao,

            @NotBlank(message = "Informe quem acionar em caso de adoecimento.")
            @Size(max = 180)
            String contatoEmergenciaNome,

            @NotBlank(message = "Informe o telefone de quem acionar em caso de adoecimento.")
            @Pattern(regexp = TELEFONE, message = MENSAGEM_TELEFONE)
            String contatoEmergenciaTelefone
    ) {
    }

    public record Autorizacoes(
            @NotNull(message = "Responda se autoriza eventos, passeios e viagens.")
            Boolean eventosPasseios,

            @NotNull(message = "Responda se a criança pode ir embora sozinha.")
            Boolean irEmboraSozinho,

            @Size(max = 500)
            String quemBusca
    ) {
    }
}
