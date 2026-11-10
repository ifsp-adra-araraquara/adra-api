package adra.ifsp.edu.br.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FusoHorarioTest {

    @Test
    @DisplayName("Testes rodam no fuso da ADRA, igual à aplicação")
    void testesNoFusoDaAdra() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ApiApplication.FUSO);
    }

    @Test
    @DisplayName("Imagem Docker sobe no fuso da ADRA")
    void dockerfileNoFusoDaAdra() throws Exception {
        assertThat(Files.readString(Path.of("Dockerfile")))
                .contains("ENV TZ=" + ApiApplication.FUSO.getId())
                .doesNotContain("ENV TZ=UTC");
    }
}
