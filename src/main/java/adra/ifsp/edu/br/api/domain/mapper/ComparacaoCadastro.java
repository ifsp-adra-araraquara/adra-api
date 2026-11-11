package adra.ifsp.edu.br.api.domain.mapper;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.CampoComparadoDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.Endereco;
import adra.ifsp.edu.br.api.domain.model.Responsavel;

/**
 * Compara a ficha com o que a família enviou na rematrícula, campo a campo,
 * em texto pronto para a tela. Termos e autorizações não entram: são da
 * matrícula de cada ano, não da ficha.
 */
@Component
public class ComparacaoCadastro {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VAZIO = "—";

    public List<CampoComparadoDTO> comparar(Assistido ficha, Collection<AssistidoResponsavel> vinculos,
                                            AssistidoRequestDTO enviado) {
        List<CampoComparadoDTO> campos = new ArrayList<>();
        campos.add(campo("Nome completo", ficha.getNomeCompleto(), enviado.nomeCompleto()));
        campos.add(campo("Data de nascimento", data(ficha.getDataNascimento()), data(enviado.dataNascimento())));
        campos.add(campo("Sexo", rotulo(ficha.getSexo()), rotulo(enviado.sexo())));
        campos.add(campo("Endereço", endereco(ficha.getEndereco()), endereco(enviado.endereco())));
        campos.add(campo("Problema de saúde",
                saude(ficha.getPossuiProblemaSaude(), ficha.getProblemaSaude()),
                saude(enviado.possuiProblemaSaude(), enviado.problemaSaude())));
        campos.add(campo("Renda familiar", dinheiro(ficha.getRendaFamiliar()), dinheiro(enviado.rendaFamiliar())));
        campos.add(campo("Programas de transferência de renda",
                programas(ficha.getRecebeProgramaTransferencia(), ficha.getProgramasTransferencia()),
                programas(enviado.recebeProgramaTransferencia(), enviado.programasTransferencia())));
        campos.add(campo("NIS", ficha.getNis(), enviado.nis()));
        campos.add(campo("Observações (contato de emergência, quem busca)", ficha.getObservacoes(), enviado.observacoes()));
        campos.addAll(responsaveis(vinculos, enviado.responsaveis()));
        return campos;
    }

    /**
     * Responsável enviado com CPF: compara nome e telefone com o vinculado de
     * mesmo CPF; sem vínculo, aparece como novo. Vinculado que não veio no
     * formulário também aparece, para a equipe decidir.
     */
    private List<CampoComparadoDTO> responsaveis(Collection<AssistidoResponsavel> vinculos,
                                                 List<VinculoFamiliarComResponsavelRequestDTO> enviados) {
        Map<String, AssistidoResponsavel> porCpf = vinculos.stream()
                .filter(v -> StringUtils.hasText(v.getResponsavel().getCpf()))
                .collect(Collectors.toMap(v -> v.getResponsavel().getCpf(), Function.identity(), (a, b) -> a));
        List<CampoComparadoDTO> campos = new ArrayList<>();
        List<String> cpfsEnviados = new ArrayList<>();

        for (VinculoFamiliarComResponsavelRequestDTO enviado : enviados == null ? List.<VinculoFamiliarComResponsavelRequestDTO>of() : enviados) {
            String parentesco = StringUtils.hasText(enviado.parentesco()) ? enviado.parentesco() : "Responsável";
            AssistidoResponsavel vinculado = enviado.cpf() != null ? porCpf.get(enviado.cpf()) : null;
            if (enviado.cpf() != null) {
                cpfsEnviados.add(enviado.cpf());
            }
            if (vinculado == null) {
                campos.add(new CampoComparadoDTO(parentesco, "Não está na ficha", comTelefone(enviado.nomeCompleto(), enviado.telefone()), true));
                continue;
            }
            Responsavel naFicha = vinculado.getResponsavel();
            campos.add(campo(parentesco, comTelefone(naFicha.getNomeCompleto(), naFicha.getTelefone()),
                    comTelefone(enviado.nomeCompleto(), enviado.telefone())));
        }

        vinculos.stream()
                .filter(v -> v.getResponsavel().getCpf() == null || !cpfsEnviados.contains(v.getResponsavel().getCpf()))
                .forEach(v -> campos.add(new CampoComparadoDTO(
                        StringUtils.hasText(v.getParentesco()) ? v.getParentesco() : "Responsável",
                        comTelefone(v.getResponsavel().getNomeCompleto(), v.getResponsavel().getTelefone()),
                        "Não informado no formulário", true)));
        return campos;
    }

    static CampoComparadoDTO campo(String rotulo, String naFicha, String enviado) {
        String ficha = textoOuVazio(naFicha);
        String formulario = textoOuVazio(enviado);
        return new CampoComparadoDTO(rotulo, ficha, formulario, !normalizar(ficha).equals(normalizar(formulario)));
    }

    /** Diferença só de maiúsculas ou de espaços não conta como mudança. */
    static String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String textoOuVazio(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : VAZIO;
    }

    private static String data(LocalDate data) {
        return data == null ? null : data.format(DATA);
    }

    /** Nomes como a família e a equipe escrevem (o nome do enum não tem acento). */
    private static final Map<String, String> ROTULOS = Map.of(
            "BOLSA_FAMILIA", "Bolsa Família",
            "AUXILIO_GAS", "Auxílio Gás",
            "BOLSA_ESCOLA", "Bolsa Escola",
            "ALUGUEL_SOCIAL", "Aluguel Social",
            "BPC", "BPC",
            "PETI", "PETI",
            "OUTRO", "Outro");

    private static String rotulo(Enum<?> valor) {
        if (valor == null) {
            return null;
        }
        if (ROTULOS.containsKey(valor.name())) {
            return ROTULOS.get(valor.name());
        }
        String texto = valor.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String endereco(Endereco endereco) {
        return endereco == null ? null
                : endereco(new EnderecoDTO(endereco.getLogradouro(), endereco.getNumero(), endereco.getBairro(),
                        endereco.getPontoReferencia()));
    }

    private static String endereco(EnderecoDTO endereco) {
        if (endereco == null || !StringUtils.hasText(endereco.logradouro())) {
            return null;
        }
        StringBuilder texto = new StringBuilder(endereco.logradouro().trim());
        if (StringUtils.hasText(endereco.numero())) {
            texto.append(", ").append(endereco.numero().trim());
        }
        if (StringUtils.hasText(endereco.bairro())) {
            texto.append(" — ").append(endereco.bairro().trim());
        }
        if (StringUtils.hasText(endereco.pontoReferencia())) {
            texto.append(" (").append(endereco.pontoReferencia().trim()).append(")");
        }
        return texto.toString();
    }

    private static String saude(Boolean possui, String descricao) {
        if (possui == null) {
            return null;
        }
        return Boolean.TRUE.equals(possui) ? "Sim" + (StringUtils.hasText(descricao) ? ": " + descricao.trim() : "") : "Não";
    }

    private static String dinheiro(BigDecimal valor) {
        return valor == null ? null : NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
    }

    private static String programas(Boolean recebe, Set<ProgramaTransferencia> programas) {
        if (recebe == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(recebe) || programas == null || programas.isEmpty()) {
            return "Não recebe";
        }
        return programas.stream().filter(Objects::nonNull).map(ComparacaoCadastro::rotulo).sorted()
                .collect(Collectors.joining(", "));
    }

    private static String comTelefone(String nome, String telefone) {
        String formatado = PreCadastroParaAssistido.telefoneFormatado(telefone);
        return StringUtils.hasText(formatado) ? nome + " — " + formatado : nome;
    }
}
