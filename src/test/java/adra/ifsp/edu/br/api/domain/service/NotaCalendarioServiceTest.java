package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.NotaCalendario;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NotaCalendarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaCalendarioServiceTest {

    @Mock
    private NotaCalendarioRepository notaCalendarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private UsuarioAutenticadoService usuarioAutenticadoService;

    @InjectMocks
    private NotaCalendarioService notaCalendarioService;

    @Test
    void deveCadastrarNotaGlobalComHorarioEDescricao() {
        Usuario coord = Usuario.builder().usuarioId(10L).nomeCompleto("Coordenador Teste").build();
        when(usuarioAutenticadoService.getUsuarioAtual()).thenReturn(coord);
        when(notaCalendarioRepository.save(any(NotaCalendario.class))).thenAnswer(inv -> {
            NotaCalendario n = inv.getArgument(0);
            n.setNotaId(42L);
            return n;
        });

        NotaCalendarioRequestDTO dto = new NotaCalendarioRequestDTO(
                LocalDate.of(2026, 11, 15),
                LocalTime.of(19, 0),
                "  Reunião Geral  ",
                "  Trazer relatórios  "
        );

        NotaCalendarioResponseDTO resposta = notaCalendarioService.cadastrar(dto);

        assertThat(resposta.notaId()).isEqualTo(42L);
        assertThat(resposta.dataNota()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(resposta.horario()).isEqualTo(LocalTime.of(19, 0));
        assertThat(resposta.titulo()).isEqualTo("Reunião Geral");
        assertThat(resposta.descricao()).isEqualTo("Trazer relatórios");
        assertThat(resposta.criadoPorId()).isEqualTo(10L);
        assertThat(resposta.criadoPorNome()).isEqualTo("Coordenador Teste");

        verify(auditoriaService).registrar(
                eq(ModuloSistema.AULAS),
                eq("nota_calendario"),
                eq(42L),
                eq(AcaoSistema.CRIAR),
                eq(null),
                any(),
                any()
        );
    }

    @Test
    void deveAtualizarEDeletarNotaExistente() {
        NotaCalendario existente = new NotaCalendario();
        existente.setNotaId(7L);
        existente.setDataNota(LocalDate.of(2026, 11, 10));
        existente.setTitulo("Evento antigo");

        when(notaCalendarioRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(notaCalendarioRepository.save(any(NotaCalendario.class))).thenAnswer(inv -> inv.getArgument(0));

        NotaCalendarioRequestDTO dto = new NotaCalendarioRequestDTO(
                LocalDate.of(2026, 11, 12),
                LocalTime.of(14, 30),
                "Evento atualizado",
                null
        );

        NotaCalendarioResponseDTO atualizada = notaCalendarioService.atualizar(7L, dto);
        assertThat(atualizada.dataNota()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(atualizada.horario()).isEqualTo(LocalTime.of(14, 30));
        assertThat(atualizada.titulo()).isEqualTo("Evento atualizado");

        notaCalendarioService.deletar(7L);
        verify(notaCalendarioRepository).delete(existente);
    }

    @Test
    void deveListarNotasPorPeriodoOuTodas() {
        NotaCalendario n = new NotaCalendario();
        n.setNotaId(1L);
        n.setDataNota(LocalDate.of(2026, 11, 20));
        n.setTitulo("Festa de encerramento");

        when(notaCalendarioRepository.findAllByOrderByDataNotaAscHorarioAsc()).thenReturn(List.of(n));
        assertThat(notaCalendarioService.listar(null, null)).hasSize(1);

        LocalDate ini = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        when(notaCalendarioRepository.findByDataNotaBetweenOrderByDataNotaAscHorarioAsc(ini, fim)).thenReturn(List.of(n));
        assertThat(notaCalendarioService.listar(ini, fim)).hasSize(1);
    }

    @Test
    void deveLancarExcecaoQuandoNotaNaoEncontrada() {
        when(notaCalendarioRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> notaCalendarioService.buscarPorId(999L))
                .isInstanceOf(EntidadeNaoEncontradaException.class);
    }
}
