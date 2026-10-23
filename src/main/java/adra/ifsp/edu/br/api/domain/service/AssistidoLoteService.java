package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.ResultadoLinhaLoteDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistidoLoteService {

    private static final int LIMITE_LINHAS = 50;

    private final AssistidoService assistidoService;
    private final ResponsavelRepository responsavelRepository;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<ResultadoLinhaLoteDTO> validar(List<AssistidoRequestDTO> itens) {
        verificarTamanho(itens);
        Map<String, String> existentes = responsaveisExistentes(itens);

        List<ResultadoLinhaLoteDTO> resultados = new ArrayList<>();
        for (int indice = 0; indice < itens.size(); indice++) {
            resultados.add(validarLinha(indice, itens, existentes));
        }
        return resultados;
    }

    private ResultadoLinhaLoteDTO validarLinha(int indice, List<AssistidoRequestDTO> itens, Map<String, String> existentes) {
        AssistidoRequestDTO item = itens.get(indice);
        Map<String, String> existentesDaLinha = cpfsResponsaveis(item).stream()
                .filter(existentes::containsKey)
                .distinct()
                .collect(Collectors.toMap(cpf -> cpf, existentes::get));

        List<String> erros = errosDaLinha(indice, itens);
        if (!erros.isEmpty()) {
            return ResultadoLinhaLoteDTO.invalida(indice, erros, existentesDaLinha);
        }
        return ResultadoLinhaLoteDTO.valida(indice, existentesDaLinha);
    }

    private List<String> errosDaLinha(int indice, List<AssistidoRequestDTO> itens) {
        AssistidoRequestDTO item = itens.get(indice);
        List<String> erros = new ArrayList<>(validator.validate(item).stream()
                .map(violacao -> violacao.getPropertyPath() + ": " + violacao.getMessage())
                .toList());

        List<VinculoFamiliarComResponsavelRequestDTO> responsaveis = Objects.requireNonNullElse(item.responsaveis(), List.of());
        if (responsaveis.isEmpty()) {
            erros.add("responsaveis: Pelo menos um responsavel deve ser informado");
        } else if (responsaveis.stream().noneMatch(VinculoFamiliarComResponsavelRequestDTO::responsavelPrincipal)) {
            erros.add("responsaveis: Pelo menos um responsavel deve ser marcado como responsavel principal");
        }

        List<String> cpfs = cpfsResponsaveis(item);
        if (Set.copyOf(cpfs).size() < cpfs.size()) {
            erros.add("responsaveis: CPF de responsavel repetido no mesmo assistido");
        }

        for (int anterior = 0; anterior < indice; anterior++) {
            AssistidoRequestDTO outro = itens.get(anterior);
            if (StringUtils.hasText(item.cpf()) && item.cpf().equals(outro.cpf())) {
                erros.add("cpf: CPF repetido na linha " + (anterior + 1));
                break;
            }
            if (mesmoNomeENascimento(outro, item) && !AssistidoService.cpfsDiferentes(outro.cpf(), item.cpf())) {
                erros.add("nomeCompleto: Assistido repetido na linha " + (anterior + 1));
                break;
            }
        }

        if (erros.isEmpty()) {
            assistidoService.motivoDuplicidade(item, null).ifPresent(erros::add);
        }
        return erros;
    }

    private boolean mesmoNomeENascimento(AssistidoRequestDTO a, AssistidoRequestDTO b) {
        return a.nomeCompleto() != null && a.nomeCompleto().trim().equalsIgnoreCase(Objects.toString(b.nomeCompleto(), "").trim())
                && Objects.equals(a.dataNascimento(), b.dataNascimento());
    }

    private Map<String, String> responsaveisExistentes(List<AssistidoRequestDTO> itens) {
        Set<String> cpfs = itens.stream()
                .flatMap(item -> cpfsResponsaveis(item).stream())
                .collect(Collectors.toSet());
        if (cpfs.isEmpty()) {
            return Map.of();
        }
        return responsavelRepository.findByCpfIn(cpfs).stream()
                .collect(Collectors.toMap(Responsavel::getCpf, Responsavel::getNomeCompleto, (primeiro, segundo) -> primeiro));
    }

    private List<String> cpfsResponsaveis(AssistidoRequestDTO item) {
        return Objects.requireNonNullElse(item.responsaveis(), List.<VinculoFamiliarComResponsavelRequestDTO>of()).stream()
                .map(VinculoFamiliarComResponsavelRequestDTO::cpf)
                .filter(cpf -> cpf != null && !cpf.isBlank())
                .toList();
    }

    private void verificarTamanho(List<AssistidoRequestDTO> itens) {
        if (itens == null || itens.isEmpty() || itens.size() > LIMITE_LINHAS) {
            throw new RegraNegocioException("O lote deve ter entre 1 e " + LIMITE_LINHAS + " assistidos");
        }
    }
}
