package adra.ifsp.edu.br.api.domain.enums;

public enum Turno {
    MANHA,
    TARDE,
    INTEGRAL
}



//package adra.ifsp.edu.br.api.domain.enums;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonValue;
//
//public enum Turno {
//    MANHA("Manhã"),
//    TARDE("Tarde"),
//    INTEGRAL("Integral");
//
//    private final String descricao;
//
//    Turno(String descricao) {
//        this.descricao = descricao;
//    }
//
//    @JsonValue
//    public String getDescricao() {
//        return descricao;
//    }
//
//    @JsonCreator
//    public static Turno fromDescricao(String descricao) {
//        for (Turno turno : values()) {
//            if (turno.descricao.equalsIgnoreCase(descricao)) {
//                return turno;
//            }
//        }
//        throw new IllegalArgumentException(
//                "Turno inválido: " + descricao + ". Valores aceitos: Manhã, Tarde, Integral.");
//    }
//}
