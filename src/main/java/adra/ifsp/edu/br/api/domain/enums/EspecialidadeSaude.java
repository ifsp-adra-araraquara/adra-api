package adra.ifsp.edu.br.api.domain.enums;

/**
 * Espelha o ENUM adra.especialidade_saude, criado na migracao deste card
 * (nao existia no schema_adra.sql original). So' e' preenchido quando
 * Usuario.nivelPermissao == PROFISSIONAL_SAUDE.
 */
public enum EspecialidadeSaude {
    NEUROLOGIA,
    PSICOPEDAGOGIA,
    PSICOLOGIA
}
