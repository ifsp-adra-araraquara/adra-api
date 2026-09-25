package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirmação pós-ChamadaFluxoDesligamentoStageIT: o rollback do
 * @Transactional realmente não deixou nada gravado em stage. Só leitura.
 */
@SpringBootTest
@ActiveProfiles("stage")
@Tag("stage")
class VerificarLimpezaStageIT {

    @Autowired
    private TurmaRepository turmaRepository;

    @Test
    void nenhumaTurmaDeTesteDeveSobrarEmStage() {
        long sobras = turmaRepository.findAll().stream()
                .filter(t -> t.getNomeTurma() != null && t.getNomeTurma().startsWith("TESTE-CA70-AUTOMATIZADO"))
                .count();
        assertThat(sobras).isZero();
    }
}
