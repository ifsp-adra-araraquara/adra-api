package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.matricula.ListaAnualDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.ListaAnualDTO.LinhaDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusMatricula;
import adra.ifsp.edu.br.api.domain.repository.EncerramentoAnoRepository;
import adra.ifsp.edu.br.api.domain.repository.MatriculaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/** Lista de atendidos por ano (histórico): uma consulta, sem nada guardado. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListaAnualService {

    private final MatriculaRepository matriculaRepository;
    private final EncerramentoAnoRepository encerramentoRepository;

    public List<Integer> anos() {
        return matriculaRepository.anosComMatricula();
    }

    public ListaAnualDTO doAno(int ano) {
        if (ano < 2000 || ano > 2100) {
            throw new RegraNegocioException("Ano inválido: " + ano + ".");
        }
        LocalDate inicio = LocalDate.of(ano, 1, 1);
        LocalDate fim = LocalDate.of(ano, 12, 31);
        LocalDate hoje = LocalDate.now();
        LocalDate ate = hoje.isBefore(fim) ? hoje : fim;

        List<LinhaDTO> linhas = matriculaRepository.listarDoAno(ano, inicio, fim, ate).stream()
                .map(l -> new LinhaDTO(l.getAssistidoId(), l.getNomeCompleto(), l.getDataNascimento(),
                        StatusMatricula.valueOf(l.getStatusMatricula()), l.getNomeTurma(), l.getEntrada(), l.getSaida(),
                        l.getTotalChamadas(), l.getPresencas(), l.getFaltas(), l.getFaltasJustificadas(),
                        l.getTotalChamadas() == 0 ? null
                                : (int) Math.round(l.getPresencas() * 100.0 / l.getTotalChamadas())))
                .toList();

        return new ListaAnualDTO(ano, encerramentoRepository.existsById(ano), ate,
                contar(linhas, StatusMatricula.ATIVA), contar(linhas, StatusMatricula.ENCERRADA),
                contar(linhas, StatusMatricula.DESLIGADA), linhas);
    }

    private static int contar(List<LinhaDTO> linhas, StatusMatricula status) {
        return (int) linhas.stream().filter(l -> l.statusMatricula() == status).count();
    }
}
