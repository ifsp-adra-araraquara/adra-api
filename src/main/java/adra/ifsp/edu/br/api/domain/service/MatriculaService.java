package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Matricula;
import adra.ifsp.edu.br.api.domain.repository.MatriculaRepository;
import lombok.RequiredArgsConstructor;

/**
 * Matrícula por ano letivo. Único lugar que cria matrícula e mexe nos termos,
 * para a ficha e a matrícula atual nunca ficarem desencontradas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MatriculaService {

    private final MatriculaRepository repository;
    private final AssistidoMapper assistidoMapper;

    /** Ano letivo de quem é cadastrado hoje pela equipe. */
    public int anoCorrente() {
        return LocalDate.now().getYear();
    }

    /**
     * Abre a matrícula do ano com os termos informados e a torna a atual do atendido.
     * O atendido já precisa estar salvo (a matrícula referencia o id dele).
     */
    public Matricula abrir(Assistido assistido, int ano, AssistidoRequestDTO dto, Long preCadastroId) {
        Matricula matricula = Matricula.nova(assistido, ano);
        matricula.setPreCadastroId(preCadastroId);
        assistidoMapper.aplicarTermos(matricula, dto, false);
        if (assistido.getStatus() == StatusGeral.INATIVO) {
            matricula.desligar();
        }
        matricula = repository.save(matricula);
        assistido.setMatriculaAtual(matricula);
        return matricula;
    }

    /**
     * Edição da ficha: os termos corrigidos valem para a matrícula atual.
     * Atendido sem matrícula (não deveria acontecer depois da carga de 2026)
     * ganha a do ano corrente, para a edição não se perder.
     */
    public void atualizarTermos(Assistido assistido, AssistidoRequestDTO dto) {
        Matricula atual = assistido.getMatriculaAtual();
        if (atual == null) {
            abrir(assistido, anoCorrente(), dto, null);
            return;
        }
        assistidoMapper.aplicarTermos(atual, dto, true);
    }

    /** Desligar/reativar o atendido acompanha a matrícula atual. */
    public void acompanharStatus(Assistido assistido) {
        Matricula atual = assistido.getMatriculaAtual();
        if (atual == null) {
            return;
        }
        if (assistido.getStatus() == StatusGeral.INATIVO) {
            atual.desligar();
        } else {
            atual.reativar();
        }
    }
}
