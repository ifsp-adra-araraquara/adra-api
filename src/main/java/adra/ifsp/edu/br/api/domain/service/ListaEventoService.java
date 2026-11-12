package adra.ifsp.edu.br.api.domain.service;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.lista.AtendidoListaEventoDTO;
import adra.ifsp.edu.br.api.domain.repository.ListaEventoRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Lista de evento/passeio (pedido da ADRA de 12/11/2026): atendidos de um ou mais grupos,
 * em ordem alfabética, para imprimir com presença e assinatura. Só o Coordenador gera.
 */
@Service
@RequiredArgsConstructor
public class ListaEventoService {

    static final int MAXIMO_GRUPOS = 50;

    private final ListaEventoRepository repository;

    @Transactional(readOnly = true)
    public List<AtendidoListaEventoDTO> atendidos(List<Long> turmaIds, boolean comResponsavel) {
        Set<Long> grupos = turmaIds == null ? Set.of() : new LinkedHashSet<>(turmaIds);
        grupos.remove(null);
        if (grupos.isEmpty()) {
            throw new RegraNegocioException("Escolha pelo menos um grupo.");
        }
        if (grupos.size() > MAXIMO_GRUPOS) {
            throw new RegraNegocioException("Escolha no máximo " + MAXIMO_GRUPOS + " grupos.");
        }
        Collator alfabetica = Collator.getInstance(Locale.of("pt", "BR"));
        alfabetica.setStrength(Collator.PRIMARY);
        return repository.atendidos(grupos, LocalDate.now(), comResponsavel).stream()
                .sorted(Comparator.comparing(AtendidoListaEventoDTO::nomeCompleto, alfabetica))
                .toList();
    }
}
