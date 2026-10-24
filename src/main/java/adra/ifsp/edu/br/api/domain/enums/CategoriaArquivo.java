package adra.ifsp.edu.br.api.domain.enums;

import java.util.Set;

/**
 * O que o arquivo é, com as regras de cada categoria num lugar só.
 * Categoria nova (ex.: laudo médico) = novo valor aqui, sem migration.
 */
public enum CategoriaArquivo {


    FOTO_PERFIL(
            Set.of("image/webp", "image/jpeg", "image/png"),
            2 * 1024 * 1024,   // 2 MB (o front já manda ~50 KB)
            512,               // maior lado da imagem, em px
            true),             // um por dono: enviar outra substitui

    DECLARACAO_RESIDENCIA(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            10 * 1024 * 1024,
            1600,
            false),

    DOCUMENTO_IDENTIDADE(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            2 * 1024 * 1024,   // mesmo limite do bucket
            1600,              // legível sem pesar
            false),     // pode ter mais de um (frente e verso)

    MATERIAL_AULA(
            Set.of("application/pdf"),
            2*1024*1024,
        1600, false
    );

    private final Set<String> mimeTypesPermitidos;
    private final long tamanhoMaximoBytes;
    private final int ladoMaximoPx;
    private final boolean unicoPorDono;

    CategoriaArquivo(Set<String> mimeTypesPermitidos, long tamanhoMaximoBytes, int ladoMaximoPx, boolean unicoPorDono) {
        this.mimeTypesPermitidos = mimeTypesPermitidos;
        this.tamanhoMaximoBytes = tamanhoMaximoBytes;
        this.ladoMaximoPx = ladoMaximoPx;
        this.unicoPorDono = unicoPorDono;
    }

    public boolean aceita(String mimeType) { return mimeTypesPermitidos.contains(mimeType); }
    public Set<String> getMimeTypesPermitidos() { return mimeTypesPermitidos; }
    public long getTamanhoMaximoBytes() { return tamanhoMaximoBytes; }
    public int getLadoMaximoPx() { return ladoMaximoPx; }
    public boolean isUnicoPorDono() { return unicoPorDono; }
}