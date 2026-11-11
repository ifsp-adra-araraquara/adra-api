package adra.ifsp.edu.br.api.domain.service;

import org.springframework.stereotype.Component;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import lombok.RequiredArgsConstructor;

/**
 * Destino dos anexos de um pré-cadastro aceito (aprovação ou rematrícula):
 * a foto vai para a ficha (substitui a anterior); documento e comprovante
 * vão para a ficha marcados com a matrícula do ano; o documento do
 * responsável vai para o responsável principal, também marcado com a
 * matrícula — assim o descarte daquele ano leva cada cópia junto.
 */
@Component
@RequiredArgsConstructor
class AnexosDoPreCadastro {

    private final ArquivoService arquivoService;
    private final AssistidoResponsavelRepository assistidoResponsavelRepository;

    void transferir(Long preCadastroId, Long assistidoId, Long matriculaId) {
        arquivoService.transferir(EntidadeArquivo.PRE_CADASTRO, preCadastroId, CategoriaArquivo.FOTO_PERFIL,
                EntidadeArquivo.ASSISTIDO, assistidoId, CategoriaArquivo.FOTO_PERFIL, null);
        arquivoService.transferir(EntidadeArquivo.PRE_CADASTRO, preCadastroId, CategoriaArquivo.DOCUMENTO_IDENTIDADE,
                EntidadeArquivo.ASSISTIDO, assistidoId, CategoriaArquivo.DOCUMENTO_IDENTIDADE, matriculaId);
        arquivoService.transferir(EntidadeArquivo.PRE_CADASTRO, preCadastroId, CategoriaArquivo.DECLARACAO_RESIDENCIA,
                EntidadeArquivo.ASSISTIDO, assistidoId, CategoriaArquivo.DECLARACAO_RESIDENCIA, matriculaId);
        assistidoResponsavelRepository.findByIdAssistidoId(assistidoId).stream()
                .filter(AssistidoResponsavel::isResponsavelPrincipal)
                .findFirst()
                .ifPresent(principal -> arquivoService.transferir(EntidadeArquivo.PRE_CADASTRO, preCadastroId,
                        CategoriaArquivo.DOCUMENTO_RESPONSAVEL, EntidadeArquivo.RESPONSAVEL,
                        principal.getId().getResponsavelId(), CategoriaArquivo.DOCUMENTO_IDENTIDADE, matriculaId));
    }
}
