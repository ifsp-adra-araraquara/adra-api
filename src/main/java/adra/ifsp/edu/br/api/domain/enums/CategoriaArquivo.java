package adra.ifsp.edu.br.api.domain.enums;

import java.util.Set;

/**
 * O que o arquivo é, com as regras de cada categoria num lugar só.
 * Categoria nova (ex.: laudo médico) = novo valor aqui, sem migration.
 */
public enum CategoriaArquivo {


    FOTO_PERFIL(
            Set.of("image/webp", "image/jpeg", "image/png"),
            Limite.BUCKET,     // o front já manda ~50 KB
            512,               // maior lado da imagem, em px
            true),             // um por dono: enviar outra substitui

    DECLARACAO_RESIDENCIA(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            Limite.BUCKET,
            1600,
            false),

    DOCUMENTO_IDENTIDADE(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            Limite.BUCKET,
            1600,              // legível sem pesar
            false),     // pode ter mais de um (frente e verso)

    /** Pré-cadastro: documento do responsável, separado do da criança (vira DOCUMENTO_IDENTIDADE do responsável na aprovação). */
    DOCUMENTO_RESPONSAVEL(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            Limite.BUCKET,
            1600,
            false),

    MATERIAL_SEMANARIO(
            Set.of("application/pdf"),
            Limite.BUCKET,
        1600, false
    ),

    /** FT-04: foto/PDF da advertência, suspensão ou comunicado de uniforme assinado pelos pais (dono ASSISTIDO). */
    DOCUMENTO_DISCIPLINAR_ASSINADO(
            Set.of("image/webp", "image/jpeg", "image/png", "application/pdf"),
            Limite.BUCKET,
            1600,              // legível sem pesar
            false);

    /**
     * Limite por arquivo do bucket "adra-arquivos" no Supabase (configurado no painel).
     * Nenhuma categoria pode passar disso: o storage recusaria o envio.
     * (Classe aninhada porque a constante do enum não enxerga campo estático declarado depois dela.)
     */
    public static final class Limite {
        public static final long BUCKET = 2L * 1024 * 1024;

        private Limite() {
        }
    }

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