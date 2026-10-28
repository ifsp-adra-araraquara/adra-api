package adra.ifsp.edu.br.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres dos testes de integração, com o schema "adra" criado pelo
 * create-schema-adra.sql. Uma configuração só para todos os ITs: com ela
 * igual, o Spring reaproveita o contexto (e o container) entre as classes
 * em vez de subir um Postgres por teste.
 *
 * <p>Uso: {@code @Import(ContainerPostgresAdra.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainerPostgresAdra {

    /**
     * O container é {@code AutoCloseable}, mas quem o fecha é o Spring: como
     * bean com {@code @ServiceConnection}, ele é parado quando o contexto de
     * teste é encerrado. O aviso de "resource leak" da IDE é falso positivo.
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        // O schema "adra" precisa existir ANTES do Hibernate criar as tabelas
        // (ddl-auto não cria schema, só tabelas dentro de um já existente) —
        // igual à migration V1 real, só que via init script.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                .withInitScript("create-schema-adra.sql");
    }
}
