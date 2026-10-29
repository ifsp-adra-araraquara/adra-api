package adra.ifsp.edu.br.api.domain.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.modulo.ModuloDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioMeRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoModuloRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioSpecification;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.mapper.ModuloMapper;
import adra.ifsp.edu.br.api.domain.mapper.UsuarioMapper;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final NivelPermissaoRepository nivelPermissaoRepository;
    private final NivelPermissaoModuloRepository nivelPermissaoModuloRepository;
    private final UsuarioMapper usuarioMapper;
    private final ModuloMapper moduloMapper;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    @Transactional(readOnly = true)
    public void validarNovoCadastro(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new RegraNegocioException("Ja existe um usuario cadastrado com este e-mail.");
        }
    }

    public UsuarioResponseDTO cadastrar(UsuarioRequestDTO dto, UUID authUid) {
        NivelPermissao nivelPermissao = buscarNivelPermissao(dto.nivelPermissao());

        Usuario usuario = usuarioMapper.paraNovaEntidade(dto, nivelPermissao, authUid);
        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "nomeCompleto", usuario.getNomeCompleto(),
                        "email", usuario.getEmail(),
                        "nivelPermissao", dto.nivelPermissao().name()
                ),
                "Cadastro de usuario (colaborador)"
        );

        return usuarioMapper.paraDTO(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(Long id) {
        return usuarioMapper.paraDTO(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarMeuPerfil() {
        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        return usuarioMapper.paraDTO(usuario, carregarModulos(usuario));
    }

    public UsuarioResponseDTO atualizarMeuPerfil(UsuarioMeRequestDTO dto) {
        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();

        Map<String, Object> valorAnterior = Map.of(
                "nomeCompleto", usuario.getNomeCompleto(),
                "telefone", Objects.requireNonNullElse(usuario.getTelefone(), "")
        );

        usuarioMapper.atualizarMeuPerfil(usuario, dto);
        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of(
                        "nomeCompleto", usuario.getNomeCompleto(),
                        "telefone", Objects.requireNonNullElse(usuario.getTelefone(), "")
                ),
                "Atualizacao do proprio perfil pelo usuario"
        );

        return usuarioMapper.paraDTO(usuario, carregarModulos(usuario));
    }

    @Transactional(readOnly = true)
    public PaginaDTO<UsuarioResponseDTO> listar(
            String busca,
            NomeNivelPermissao perfil,
            Boolean ativo,
            int pagina,
            int tamanho) {
        Specification<Usuario> spec = UsuarioSpecification.comFiltros(busca, perfil, ativo);
        PageRequest pageRequest = PageRequest.of(pagina, tamanho, Sort.by("nomeCompleto").ascending());
        return PaginaDTO.de(usuarioRepository.findAll(spec, pageRequest).map(usuarioMapper::paraDTO));
    }

    /**
     * Lista enxuta (sem paginação) dos oficineiros ativos, usada pra
     * preencher selects no front (ex.: modal de criar turma).
     */
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarOficineiros() {
        Specification<Usuario> spec = UsuarioSpecification.comFiltros(null, NomeNivelPermissao.OFICINEIRO, true);
        return usuarioRepository.findAll(spec, Sort.by("nomeCompleto").ascending())
                .stream()
                .map(usuarioMapper::paraDTO)
                .toList();
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = buscarEntidadePorId(id);

        if (usuarioRepository.existsByEmailIgnoreCaseAndUsuarioIdNot(dto.email(), id)) {
            throw new RegraNegocioException("Ja existe outro usuario cadastrado com este e-mail.");
        }

        NivelPermissao nivelPermissao = buscarNivelPermissao(dto.nivelPermissao());

        Map<String, Object> valorAnterior = Map.of(
                "nomeCompleto", usuario.getNomeCompleto(),
                "email", usuario.getEmail(),
                "nivelPermissao", usuario.getNivelPermissao().getNome().name()
        );

        usuarioMapper.atualizarEntidade(usuario, dto, nivelPermissao);
        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of(
                        "nomeCompleto", usuario.getNomeCompleto(),
                        "email", usuario.getEmail(),
                        "nivelPermissao", dto.nivelPermissao().name()
                ),
                "Atualizacao de dados cadastrais do usuario"
        );

        return usuarioMapper.paraDTO(usuario);
    }

    /** Ativa/inativa - nunca ha' delete fisico (ver comentario na entidade Usuario). */
    public UsuarioResponseDTO alterarStatus(Long id, UsuarioStatusRequestDTO dto) {
        Usuario usuario = buscarEntidadePorId(id);
        boolean statusAnterior = usuario.isAtivo();
        usuario.setAtivo(dto.ativo());
        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.EDITAR,
                Map.of("ativo", statusAnterior),
                Map.of("ativo", usuario.isAtivo()),
                "Alteracao de status do usuario"
        );

        return usuarioMapper.paraDTO(usuario);
    }

    public void registrarRedefinicaoSenha(Usuario usuario) {
        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.EDITAR,
                null,
                Map.of("linkRedefinicaoSenhaGerado", true),
                "Link de redefinicao de senha gerado pelo Administrador");
    }

    Usuario buscarEntidadePorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuario nao encontrado: id " + id));
    }

    private NivelPermissao buscarNivelPermissao(NomeNivelPermissao nome) {
        return nivelPermissaoRepository.findByNome(nome)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        "Nivel de permissao '" + nome + "' nao encontrado. Rode a migracao/seed antes de cadastrar usuarios."));
    }

    private List<ModuloDTO> carregarModulos(Usuario usuario) {
        if (usuario.getNivelPermissao() == null || usuario.getNivelPermissao().getNivelPermissaoId() == null) {
            return List.of();
        }
        return nivelPermissaoModuloRepository
                .findComModuloById_NivelPermissaoIdOrderByOrdemAsc(usuario.getNivelPermissao().getNivelPermissaoId())
                .stream()
                .map(npm -> moduloMapper.paraDTO(npm.getModulo()))
                .toList();
    }
}
