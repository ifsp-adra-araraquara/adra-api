package adra.ifsp.edu.br.api.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Turno {
    MANHA("Manhã"),
    TARDE("Tarde"),
    INTEGRAL("Integral");

    private final String descricao;

    Turno(String descricao) {
        this.descricao = descricao;
    }

    @JsonValue
    public String getDescricao() {
        return descricao;
    }

    @JsonCreator
    public static Turno fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        // Aceita variações de "Manhã"
        if (normalized.equals("MANHA") || normalized.equals("MANHÃ")) {
            return MANHA;
        }

        // Aceita variações de "Tarde"
        if (normalized.equals("TARDE")) {
            return TARDE;
        }

        // Aceita variações de "Integral"
        if (normalized.equals("INTEGRAL")) {
            return INTEGRAL;
        }

        // Tenta converter pelo nome do enum (MANHA, TARDE, INTEGRAL)
        try {
            return Turno.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Turno inválido: " + value + ". Valores aceitos: Manhã, Tarde, Integral.");
        }
    }
}
