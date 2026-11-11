package adra.ifsp.edu.br.api;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {

	/**
	 * Fuso da ADRA. Define o "hoje" de LocalDate.now() (chamada do dia, entrada no grupo)
	 * e o horário gravado em LocalDateTime. Em UTC, depois das 21h já era o dia seguinte.
	 */
	public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	public static void main(String[] args) {
		// Antes de subir o Spring: as conexões com o banco herdam o fuso da JVM.
		TimeZone.setDefault(TimeZone.getTimeZone(FUSO));
		SpringApplication.run(ApiApplication.class, args);
	}

}
