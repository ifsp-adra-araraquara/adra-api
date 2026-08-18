package adra.ifsp.edu.br.api.domain.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.EspecialidadeSaude;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.mapper.UsuarioMapper;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private static final Set<NomeNivelPermissao> PERFIS_DO_MVP = EnumSet.of(
            NomeNivelPermissao.ADMINISTRADOR,
            NomeNivelPermissao.COORDENADOR,
            NomeNivelPermissao.SOCIOPEDAGOGICO);

    private final UsuarioRepository usuarioRepository;
    private final NivelPermissaoRepository nivelPermissaoRepository;
    private final UsuarioMapper usuarioMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public void validarNovoCadastro(UsuarioRequestDTO dto) {
        if (!PERFIS_DO_MVP.contains(dto.nivelPermissao())) {
            throw new RegraNegocioException(
                    "Perfil fora do escopo do MVP: " + dto.nivelPermissao() + ".");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new RegraNegocioException("Ja existe um usuario cadastrado com este e-mail.");
        }
        validarEspecialidade(dto.nivelPermissao(), dto.especialidade());
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
    public List<UsuarioResponseDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(usuarioMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = buscarEntidadePorId(id);

        if (usuarioRepository.existsByEmailIgnoreCaseAndUsuarioIdNot(dto.email(), id)) {
            throw new RegraNegocioException("Ja existe outro usuario cadastrado com este e-mail.");
        }

        NivelPermissao nivelPermissao = buscarNivelPermissao(dto.nivelPermissao());
        validarEspecialidade(dto.nivelPermissao(), dto.especialidade());

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

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuario nao encontrado: " + email));
    }

    public void registrarSenhaDefinida(Usuario usuario) {
        auditoriaService.registrar(
                ModuloSistema.USUARIOS,
                "usuario",
                usuario.getUsuarioId(),
                AcaoSistema.EDITAR,
                null,
                Map.of("senhaRedefinida", true),
                "Senha definida pelo Administrador");
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

    /**
     * Regra do card: especialidade e' obrigatoria QUANDO o nivel for
     * PROFISSIONAL_SAUDE, e nao se aplica a nenhum outro perfil.
     */
    private void validarEspecialidade(NomeNivelPermissao nivel, EspecialidadeSaude especialidade) {
        if (nivel == NomeNivelPermissao.PROFISSIONAL_SAUDE && especialidade == null) {
            throw new RegraNegocioException(
                    "Especialidade e' obrigatoria para usuarios com nivel PROFISSIONAL_SAUDE.");
        }
        if (nivel != NomeNivelPermissao.PROFISSIONAL_SAUDE && especialidade != null) {
            throw new RegraNegocioException(
                    "Especialidade so' deve ser informada para usuarios com nivel PROFISSIONAL_SAUDE.");
        }
    }


    public UsuarioResponseDTO autenticar(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.email())
                .filter(Usuario::isAtivo)
                .filter(u -> passwordEncoder.matches(dto.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new CredenciaisInvalidasException("Email ou senha invalidos"));

        usuario.setUltimoLogin(OffsetDateTime.now());
        usuarioRepository.save(usuario);

        List<ModuloDTO> modulos = nivelPermissaoModuloRepository
                .findComModuloById_NivelPermissaoIdOrderByOrdemAsc(usuario.getNivelPermissao().getNivelPermissaoId())
                .stream()
                .map(npm -> moduloMapper.paraDTO(npm.getModulo()))
                .toList();

        return usuarioMapper.paraDTO(usuario, modulos);
    }
}
