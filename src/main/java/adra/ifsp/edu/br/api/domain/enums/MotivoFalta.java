package adra.ifsp.edu.br.api.domain.enums;

/**
 * Lista fechada + OUTRO (ver task do CA-65: "valores a confirmar com a coordenação").
 * Os valores abaixo são um ponto de partida — ajuste antes de fechar a migration,
 * já que qualquer mudança depois disso exige migrar dados existentes.
 */
public enum MotivoFalta {
    SAUDE,
    VIAGEM,
    TRABALHO,
    MOTIVO_FAMILIAR,
    OUTRO
}