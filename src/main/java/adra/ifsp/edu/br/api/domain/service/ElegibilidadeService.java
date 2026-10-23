package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.ElegibilidadeDTO;
import adra.ifsp.edu.br.api.domain.model.ParametroSistema;
import adra.ifsp.edu.br.api.domain.repository.ParametroSistemaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Regras de elegibilidade da família ao programa. O limite de renda familiar
 * fica na tabela parametro_sistema; enquanto estiver null, nenhuma família é barrada.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElegibilidadeService {

    private final ParametroSistemaRepository parametroRepository;

    public ElegibilidadeDTO criterios() {
        return new ElegibilidadeDTO(limiteRendaFamiliar().orElse(null));
    }

    public Optional<BigDecimal> limiteRendaFamiliar() {
        return parametroRepository.findById(ParametroSistema.RENDA_FAMILIAR_LIMITE)
                .map(ParametroSistema::getValorNumerico);
    }

    /** Lança {@link RegraNegocioException} se a renda informada passar do limite configurado. */
    public void validarRendaFamiliar(BigDecimal rendaFamiliar) {
        if (rendaFamiliar == null) {
            return;
        }
        limiteRendaFamiliar()
                .filter(limite -> rendaFamiliar.compareTo(limite) > 0)
                .ifPresent(limite -> {
                    String valor = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(limite);
                    throw new RegraNegocioException(
                            "Renda familiar acima do limite do programa (" + valor + "). A familia nao pode ser cadastrada.");
                });
    }
}
