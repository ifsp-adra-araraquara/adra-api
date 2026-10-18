package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.service.IndicadorFrequencia;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

@Component
public class AssistidoMapper {

    public Assistido paraNovaEntidade(AssistidoRequestDTO dto) {
        Assistido assistido = Assistido.builder()
                .nomeCompleto(dto.nomeCompleto())
                .dataNascimento(dto.dataNascimento())
                .cpf(cpfOuNulo(dto.cpf()))
                .dataEntrada(dto.dataEntrada() != null ? dto.dataEntrada() : LocalDate.now())
                .necessidadesEspecificas(dto.necessidadesEspecificas())
                .observacoes(dto.observacoes())
                .sexo(dto.sexo())
                .possuiProblemaSaude(dto.possuiProblemaSaude())
                .problemaSaude(descricaoSeMarcado(dto.possuiProblemaSaude(), dto.problemaSaude()))
                .pcd(dto.pcd())
                .deficiencia(descricaoSeMarcado(dto.pcd(), dto.deficiencia()))
                .escolaNome(textoOuNulo(dto.escolaNome()))
                .escolaAnoSerie(textoOuNulo(dto.escolaAnoSerie()))
                .escolaTurno(dto.escolaTurno())
                .estudaContraturno(dto.estudaContraturno())
                .endereco(EnderecoMapper.paraEntidade(dto.endereco()))
                .build();
        aplicarSocioeconomico(assistido, dto);
        aplicarTermos(assistido, dto, false);
        return assistido;
    }

    /** Atualiza apenas os campos cadastrais editaveis por este card (nao mexe em status/contadores). */
    public void atualizarEntidade(Assistido entidade, AssistidoRequestDTO dto) {
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setDataNascimento(dto.dataNascimento());
        entidade.setCpf(cpfOuNulo(dto.cpf()));
        if (dto.dataEntrada() != null) {
            entidade.setDataEntrada(dto.dataEntrada());
        }
        entidade.setNecessidadesEspecificas(dto.necessidadesEspecificas());
        entidade.setObservacoes(dto.observacoes());
        entidade.setSexo(dto.sexo());
        entidade.setPossuiProblemaSaude(dto.possuiProblemaSaude());
        entidade.setProblemaSaude(descricaoSeMarcado(dto.possuiProblemaSaude(), dto.problemaSaude()));
        entidade.setPcd(dto.pcd());
        entidade.setDeficiencia(descricaoSeMarcado(dto.pcd(), dto.deficiencia()));
        entidade.setEscolaNome(textoOuNulo(dto.escolaNome()));
        entidade.setEscolaAnoSerie(textoOuNulo(dto.escolaAnoSerie()));
        entidade.setEscolaTurno(dto.escolaTurno());
        entidade.setEstudaContraturno(dto.estudaContraturno());
        entidade.setEndereco(EnderecoMapper.paraEntidade(dto.endereco()));
        aplicarSocioeconomico(entidade, dto);
        aplicarTermos(entidade, dto, true);
    }

    private void aplicarSocioeconomico(Assistido entidade, AssistidoRequestDTO dto) {
        entidade.setRendaFamiliar(dto.rendaFamiliar());
        entidade.setRecebeProgramaTransferencia(dto.recebeProgramaTransferencia());
        entidade.setNis(textoOuNulo(dto.nis()));

        Set<ProgramaTransferencia> programas = Boolean.TRUE.equals(dto.recebeProgramaTransferencia())
                && dto.programasTransferencia() != null
                ? dto.programasTransferencia()
                : Set.of();
        // mesma instancia da colecao (orphan/dirty checking do Hibernate)
        entidade.getProgramasTransferencia().clear();
        entidade.getProgramasTransferencia().addAll(programas);
        entidade.setProgramaTransferenciaOutro(programas.contains(ProgramaTransferencia.OUTRO)
                ? textoOuNulo(dto.programaTransferenciaOutro())
                : null);
    }

    /**
     * Termos: no cadastro, null vira "nao autorizado"; na edicao, null mantem o
     * valor atual. A data do aceite LGPD e' registrada quando ele passa a true.
     */
    private void aplicarTermos(Assistido entidade, AssistidoRequestDTO dto, boolean edicao) {
        boolean aceiteAnterior = entidade.isAceiteLgpd();
        entidade.setAceiteLgpd(valorTermo(dto.aceiteLgpd(), aceiteAnterior, edicao));
        if (entidade.isAceiteLgpd() && !aceiteAnterior) {
            entidade.setAceiteLgpdEm(OffsetDateTime.now());
        } else if (!entidade.isAceiteLgpd()) {
            entidade.setAceiteLgpdEm(null);
        }
        entidade.setAutorizaUsoImagem(valorTermo(dto.autorizaUsoImagem(), entidade.isAutorizaUsoImagem(), edicao));
        entidade.setAutorizaEventosPasseios(valorTermo(dto.autorizaEventosPasseios(), entidade.isAutorizaEventosPasseios(), edicao));
        entidade.setPodeIrEmboraSozinho(valorTermo(dto.podeIrEmboraSozinho(), entidade.isPodeIrEmboraSozinho(), edicao));
        entidade.setCienteFrequenciaObrigatoria(valorTermo(dto.cienteFrequenciaObrigatoria(), entidade.isCienteFrequenciaObrigatoria(), edicao));
    }

    private boolean valorTermo(Boolean informado, boolean atual, boolean edicao) {
        if (informado == null) {
            return edicao && atual;
        }
        return informado;
    }

    public AssistidoResponseDTO paraDTO(Assistido entidade) {
        return paraDTO(entidade, null, null);
    }

    /**
     * Linha da listagem: além do cadastro, o responsável principal (ou
     * {@code null}) e os indicadores de frequência (ou {@code null}).
     */
    public AssistidoResponseDTO paraDTO(Assistido entidade, AssistidoResponsavel responsavel, IndicadorFrequencia frequencia) {
        Long turmaId = entidade.getTurma() != null ? entidade.getTurma().getTurmaId() : null;
        String nomeTurma = entidade.getTurma() != null ? entidade.getTurma().getNomeTurma() : null;

        return new AssistidoResponseDTO(
                entidade.getAssistidoId(),
                entidade.getNomeCompleto(),
                entidade.getDataNascimento(),
                entidade.getCpf(),
                entidade.getDataEntrada(),
                entidade.getDataSaida(),
                entidade.getMotivoSaida(),
                entidade.getNecessidadesEspecificas(),
                entidade.getObservacoes(),
                entidade.getSexo(),
                entidade.getPossuiProblemaSaude(),
                entidade.getProblemaSaude(),
                entidade.getPcd(),
                entidade.getDeficiencia(),
                entidade.getEscolaNome(),
                entidade.getEscolaAnoSerie(),
                entidade.getEscolaTurno(),
                entidade.getEstudaContraturno(),
                EnderecoMapper.paraDTO(entidade.getEndereco()),
                entidade.getRendaFamiliar(),
                entidade.getRecebeProgramaTransferencia(),
                Set.copyOf(entidade.getProgramasTransferencia()),
                entidade.getProgramaTransferenciaOutro(),
                entidade.getNis(),
                entidade.isAceiteLgpd(),
                entidade.getAceiteLgpdEm(),
                entidade.isAutorizaUsoImagem(),
                entidade.isAutorizaEventosPasseios(),
                entidade.isPodeIrEmboraSozinho(),
                entidade.isCienteFrequenciaObrigatoria(),
                entidade.getStatus(),
                turmaId,
                nomeTurma,
                entidade.getTotalOcorrenciasAtivas(),
                entidade.getTotalAdvertenciasAtivas(),
                entidade.getTotalSuspensoes(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm(),
                responsavel != null ? responsavel.getResponsavel().getNomeCompleto() : null,
                responsavel != null ? responsavel.getParentesco() : null,
                responsavel != null ? responsavel.getResponsavel().getTelefone() : null,
                frequencia != null ? frequencia.percentual() : null,
                frequencia != null ? frequencia.totalAulas() : null,
                frequencia != null ? frequencia.faltasConsecutivas() : null,
                frequencia != null ? frequencia.emAcompanhamento() : null,
                null,
                null
        );
    }

    private String cpfOuNulo(String cpf) {
        return StringUtils.hasText(cpf) ? cpf : null;
    }

    private String textoOuNulo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    /** Descrição (problema de saúde / deficiência) só é guardada quando o indicador está marcado. */
    private String descricaoSeMarcado(Boolean marcado, String descricao) {
        return Boolean.TRUE.equals(marcado) ? textoOuNulo(descricao) : null;
    }
}
