package adra.ifsp.edu.br.api.domain.dto.precadastro;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Foto e documentos opcionais do link público (partes do multipart). Vazios já vêm filtrados. */
public record AnexosPreCadastro(
        MultipartFile foto,
        List<MultipartFile> documentosCrianca,
        List<MultipartFile> documentosResponsavel
) {
    public AnexosPreCadastro {
        foto = foto != null && !foto.isEmpty() ? foto : null;
        documentosCrianca = semVazios(documentosCrianca);
        documentosResponsavel = semVazios(documentosResponsavel);
    }

    public static AnexosPreCadastro nenhum() {
        return new AnexosPreCadastro(null, null, null);
    }

    private static List<MultipartFile> semVazios(List<MultipartFile> arquivos) {
        return arquivos == null ? List.of() : arquivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
    }
}
