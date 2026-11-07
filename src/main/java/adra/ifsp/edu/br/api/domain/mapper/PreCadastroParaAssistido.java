package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Mae;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Pai;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.ResponsavelLegal;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o cadastro do atendido a partir do que a família enviou, para a equipe
 * revisar e aprovar sem redigitar. Só entra como responsável quem tem CPF
 * (o cadastro exige); quem não tem, o contato de emergência e "quem busca"
 * vão para as observações do atendido.
 */
@Component
public class PreCadastroParaAssistido {

    public AssistidoRequestDTO montar(PreCadastro preCadastro, LocalDate hoje) {
        PreCadastroDadosDTO dados = preCadastro.getDados();
        EnderecoDTO enderecoFamilia = dados.enderecoFamilia();

        return new AssistidoRequestDTO(
                preCadastro.getNomeCompleto(),
                preCadastro.getDataNascimento(),
                preCadastro.getCpf(),
                hoje,
                null,
                observacoes(dados),
                null,
                preCadastro.getSexo(),
                dados.saude().possuiProblema(),
                dados.saude().descricao(),
                null,
                null,
                null,
                null,
                null,
                null,
                enderecoDaCrianca(dados),
                dados.rendaFamiliar(),
                dados.transferencia().recebe(),
                dados.transferencia().programas(),
                dados.transferencia().outro(),
                dados.transferencia().nis(),
                dados.termos().aceiteLgpd(),
                dados.termos().autorizaUsoImagem(),
                dados.autorizacoes().eventosPasseios(),
                dados.autorizacoes().irEmboraSozinho(),
                dados.termos().cienteFrequencia(),
                responsaveis(dados, enderecoFamilia),
                null);
    }

    private EnderecoDTO enderecoDaCrianca(PreCadastroDadosDTO dados) {
        EnderecoDTO daCrianca = dados.crianca() != null ? dados.crianca().endereco() : null;
        return daCrianca != null && StringUtils.hasText(daCrianca.logradouro()) ? daCrianca : dados.enderecoFamilia();
    }

    private List<VinculoFamiliarComResponsavelRequestDTO> responsaveis(PreCadastroDadosDTO dados, EnderecoDTO endereco) {
        List<VinculoFamiliarComResponsavelRequestDTO> responsaveis = new ArrayList<>();
        Mae mae = dados.mae();
        Pai pai = dados.pai();
        ResponsavelLegal legal = dados.responsavelLegal();

        if (mae != null) {
            String observacao = mae.estadoCivil() == null && StringUtils.hasText(mae.estadoCivilOutro())
                    ? "Estado civil: " + mae.estadoCivilOutro() : null;
            responsaveis.add(new VinculoFamiliarComResponsavelRequestDTO(mae.nomeCompleto(), mae.dataNascimento(),
                    mae.cpf(), mae.telefone(), null, endereco, observacao, mae.escolaridade(), mae.estadoCivil(),
                    mae.profissao(), mae.renda(), "Mãe", true, false, false));
        }
        if (pai != null && StringUtils.hasText(pai.cpf())) {
            responsaveis.add(new VinculoFamiliarComResponsavelRequestDTO(pai.nomeCompleto(), null,
                    pai.cpf(), pai.telefone(), null, endereco, null, null, null,
                    pai.profissao(), pai.renda(), "Pai", false, false, false));
        }
        if (legal != null && StringUtils.hasText(legal.cpf())) {
            responsaveis.add(new VinculoFamiliarComResponsavelRequestDTO(legal.nomeCompleto(), null,
                    legal.cpf(), legal.telefone(), null, endereco, null, null, null,
                    null, null, legal.parentesco(), mae == null, false, false));
        }
        return responsaveis;
    }

    private String observacoes(PreCadastroDadosDTO dados) {
        List<String> linhas = new ArrayList<>();
        linhas.add("Contato de emergência: " + dados.saude().contatoEmergenciaNome()
                + " — " + telefoneFormatado(dados.saude().contatoEmergenciaTelefone()));
        if (StringUtils.hasText(dados.autorizacoes().quemBusca())) {
            linhas.add("Quem busca: " + dados.autorizacoes().quemBusca());
        }
        Pai pai = dados.pai();
        if (pai != null && !StringUtils.hasText(pai.cpf())) {
            linhas.add("Pai (sem CPF): " + comTelefone(pai.nomeCompleto(), pai.telefone()));
        }
        ResponsavelLegal legal = dados.responsavelLegal();
        if (legal != null && !StringUtils.hasText(legal.cpf())) {
            linhas.add("Responsável legal (sem CPF): "
                    + comTelefone(legal.nomeCompleto() + " (" + legal.parentesco() + ")", legal.telefone()));
        }
        return String.join("\n", linhas);
    }

    private String comTelefone(String nome, String telefone) {
        return StringUtils.hasText(telefone) ? nome + " — " + telefoneFormatado(telefone) : nome;
    }

    /** 16912345678 → (16) 91234-5678; 1633334444 → (16) 3333-4444. */
    static String telefoneFormatado(String digitos) {
        if (digitos == null || digitos.length() < 10) {
            return digitos;
        }
        String ddd = digitos.substring(0, 2);
        String numero = digitos.substring(2);
        int corte = numero.length() - 4;
        return "(" + ddd + ") " + numero.substring(0, corte) + "-" + numero.substring(corte);
    }
}
