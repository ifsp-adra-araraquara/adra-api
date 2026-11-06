package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.repository.FaltaJustificadaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresencaMapper {

    private final FaltaJustificadaRepository faltaJustificadaRepository;

    /**
     * Sincroniza o satélite FaltaJustificada com o status já salvo em `presenca`.
     * Só chame depois do save (precisa de presenca.getPresencaId() != null).
     * CA-65.2: FALTA_JUSTIFICADA exige motivo_falta (FT-13: Atestado ou Suspensão; observação opcional).
     * Fora de FALTA_JUSTIFICADA, remove qualquer FaltaJustificada residual de um
     * reenvio anterior (CA-65.4 — reenvio atualiza, não deixa lixo pra trás).
     */
    public void sincronizarFaltaJustificada(Presenca presenca, MotivoFalta motivoFalta, String observacao) {
        if (presenca.getStatusPresenca() != StatusPresenca.FALTA_JUSTIFICADA) {
            faltaJustificadaRepository.deleteByPresenca_PresencaId(presenca.getPresencaId());
            return;
        }

        validarJustificativa(motivoFalta, observacao);

        FaltaJustificada faltaJustificada = faltaJustificadaRepository
                .findByPresenca_PresencaId(presenca.getPresencaId())
                .orElseGet(() -> {
                    FaltaJustificada nova = new FaltaJustificada();
                    nova.setPresenca(presenca);
                    return nova;
                });

        faltaJustificada.setMotivoFalta(motivoFalta);
        faltaJustificada.setObservacao(observacao);
        faltaJustificadaRepository.save(faltaJustificada);
    }

    /** CA-65.2: falta justificada exige o motivo (FT-13: a observação é opcional). */
    public static void validarJustificativa(MotivoFalta motivoFalta, String observacao) {
        if (motivoFalta == null) {
            throw new RegraNegocioException("Falta justificada exige o motivo da falta.");
        }
    }
}
