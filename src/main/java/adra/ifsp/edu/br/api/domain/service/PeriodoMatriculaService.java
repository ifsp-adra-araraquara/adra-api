package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.matricula.InscricaoAbertaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.PeriodoMatricula;
import adra.ifsp.edu.br.api.domain.repository.PeriodoMatriculaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Período de matrícula: decide de que ano é cada inscrição do link público.
 * Sem período aberto, a inscrição é do ano corrente (o link nunca fica fora do ar).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PeriodoMatriculaService {

    private final PeriodoMatriculaRepository repository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public InscricaoAbertaDTO inscricaoDeHoje() {
        LocalDate hoje = LocalDate.now();
        return repository.findAbertoEm(hoje)
                .map(periodo -> new InscricaoAbertaDTO(periodo.getAno(), true))
                .orElseGet(() -> new InscricaoAbertaDTO(hoje.getYear(), false));
    }

    /** Ano gravado no pré-cadastro enviado hoje. */
    @Transactional(readOnly = true)
    public int anoDaInscricao() {
        return inscricaoDeHoje().ano();
    }

    @Transactional(readOnly = true)
    public List<PeriodoMatriculaDTO> listar() {
        LocalDate hoje = LocalDate.now();
        return repository.findAllByOrderByAnoDesc().stream().map(p -> PeriodoMatriculaDTO.de(p, hoje)).toList();
    }

    /** Abre (ou ajusta) o período do ano. Só para o ano corrente ou os seguintes. */
    public PeriodoMatriculaDTO salvar(int ano, PeriodoMatriculaRequestDTO dto) {
        LocalDate hoje = LocalDate.now();
        if (ano < hoje.getYear()) {
            throw new RegraNegocioException("Só é possível abrir matrícula para " + hoje.getYear() + " ou anos seguintes.");
        }
        if (ano > hoje.getYear() + 1) {
            throw new RegraNegocioException("Abra a matrícula de " + ano + " a partir de " + (ano - 1) + ".");
        }
        if (dto.fim() != null && dto.fim().isBefore(dto.inicio())) {
            throw new RegraNegocioException("O fim das inscrições não pode ser antes do início.");
        }

        PeriodoMatricula periodo = repository.findById(ano).orElseGet(() -> new PeriodoMatricula(ano));
        Map<String, Object> antes = periodo.getInicio() == null ? null : datas(periodo);
        periodo.setInicio(dto.inicio());
        periodo.setFim(dto.fim());
        periodo.setAlteradoPor(usuarioAutenticadoService.getUsuarioAtual());
        repository.save(periodo);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "periodo_matricula", (long) ano,
                antes == null ? AcaoSistema.CRIAR : AcaoSistema.EDITAR, antes, datas(periodo),
                "Período de matrícula " + ano);
        return PeriodoMatriculaDTO.de(periodo, hoje);
    }

    private static Map<String, Object> datas(PeriodoMatricula periodo) {
        Map<String, Object> valores = new HashMap<>();
        valores.put("inicio", periodo.getInicio().toString());
        valores.put("fim", periodo.getFim() == null ? null : periodo.getFim().toString());
        return valores;
    }
}
