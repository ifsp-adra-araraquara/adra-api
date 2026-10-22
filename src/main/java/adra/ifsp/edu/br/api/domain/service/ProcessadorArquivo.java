package adra.ifsp.edu.br.api.domain.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.exception.ArquivoInvalidoException;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

/**
 * Nunca confia no front: descobre o tipo pelos bytes (não pelo Content-Type),
 * barra imagem gigante antes de decodificar e recomprime toda imagem.
 *
 * Toda imagem vira JPEG (o re-encode descarta EXIF/GPS) — o Java não grava WebP sem biblioteca nativa.
 */
@Component
public class ProcessadorArquivo {

    /** Acima disso nem decodifica (proteção contra "image bomb" estourando a memória): ~64 MB decodificada. */
    private static final long PIXELS_MAXIMOS_ENTRADA = 4096L * 4096;
    private static final float QUALIDADE_JPEG = 0.8f;

    public record ArquivoProcessado(byte[] conteudo, String mimeType, String extensao) {
    }

    private record Dimensao(int largura, int altura) {
    }

    public ProcessadorArquivo() {
        // No jar do Spring Boot o ImageIO pode não achar os plugins do TwelveMonkeys sozinho.
        ImageIO.scanForPlugins();
    }

    public ArquivoProcessado processar(byte[] original, CategoriaArquivo categoria) {
        String tipoReal = detectarTipo(original);
        if (tipoReal == null || !categoria.aceita(tipoReal)) {
            throw new ArquivoInvalidoException("Tipo de arquivo nao permitido para " + categoria
                    + ". Aceitos: " + String.join(", ", categoria.getMimeTypesPermitidos()));
        }

        ArquivoProcessado processado = "application/pdf".equals(tipoReal)
                ? new ArquivoProcessado(original, tipoReal, "pdf") // PDF já é compactado por dentro
                : processarImagem(original, categoria);

        if (processado.conteudo().length > categoria.getTamanhoMaximoBytes()) {
            throw new ArquivoInvalidoException("Arquivo muito grande para " + categoria + ".");
        }
        return processado;
    }

    private ArquivoProcessado processarImagem(byte[] original, CategoriaArquivo categoria) {
        Dimensao d = lerDimensoes(original);
        if ((long) d.largura() * d.altura() > PIXELS_MAXIMOS_ENTRADA) {
            throw new ArquivoInvalidoException("Imagem com dimensoes muito grandes.");
        }

        int ladoMaximo = categoria.getLadoMaximoPx();

        try {
            // Lê do stream (decodifica uma vez só), que respeita a rotação EXIF da foto do celular.
            Thumbnails.Builder<?> builder = Thumbnails.of(new ByteArrayInputStream(original));

            if (categoria == CategoriaArquivo.FOTO_PERFIL) {
                int lado = Math.min(ladoMaximo, Math.min(d.largura(), d.altura()));
                builder = builder.size(lado, lado).crop(Positions.CENTER);
            } else if (Math.max(d.largura(), d.altura()) > ladoMaximo) {
                builder = builder.size(ladoMaximo, ladoMaximo); // mantém proporção
            } else {
                builder = builder.scale(1.0); // nunca amplia
            }

            // Achata transparência em fundo branco, senão o JPEG fica preto.
            BufferedImage reduzida = comFundoBranco(builder.asBufferedImage());

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            Thumbnails.of(reduzida)
                    .scale(1.0)
                    .outputFormat("jpg")
                    .outputQuality(QUALIDADE_JPEG)
                    .toOutputStream(saida);
            return new ArquivoProcessado(saida.toByteArray(), "image/jpeg", "jpg");
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Imagem corrompida ou ilegivel.");
        }
    }

    private Dimensao lerDimensoes(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new ArquivoInvalidoException("Formato de imagem nao suportado.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(in);
                return new Dimensao(leitor.getWidth(0), leitor.getHeight(0));
            } finally {
                leitor.dispose();
            }
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Imagem corrompida ou ilegivel.");
        }
    }

    private static BufferedImage comFundoBranco(BufferedImage imagem) {
        BufferedImage rgb = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
            g.drawImage(imagem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Assinatura ("magic bytes") dos formatos aceitos. */
    static String detectarTipo(byte[] b) {
        if (comeca(b, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (comeca(b, 0x89, 'P', 'N', 'G')) return "image/png";
        if (comeca(b, 'R', 'I', 'F', 'F') && b.length > 12
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (comeca(b, '%', 'P', 'D', 'F', '-')) return "application/pdf";
        return null;
    }

    private static boolean comeca(byte[] b, int... assinatura) {
        if (b.length < assinatura.length) return false;
        for (int i = 0; i < assinatura.length; i++) {
            if ((b[i] & 0xFF) != assinatura[i]) return false;
        }
        return true;
    }
}