package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;

/**
 * Feriados nacionais (fixos e móveis, a partir da Páscoa). Mesma lista do front
 * (shared/utils/feriados.util.ts), sem os pontos facultativos (Carnaval e
 * Corpus Christi), que a ADRA decide cadastrando em "Dias sem aula".
 */
public final class FeriadosNacionais {

    private static final Set<MonthDay> FIXOS = Set.of(
            MonthDay.of(1, 1),   // Confraternização Universal
            MonthDay.of(4, 21),  // Tiradentes
            MonthDay.of(5, 1),   // Dia do Trabalho
            MonthDay.of(9, 7),   // Independência
            MonthDay.of(10, 12), // Nossa Senhora Aparecida
            MonthDay.of(11, 2),  // Finados
            MonthDay.of(11, 15), // Proclamação da República
            MonthDay.of(11, 20), // Consciência Negra (Lei 14.759/2023)
            MonthDay.of(12, 25)  // Natal
    );

    private FeriadosNacionais() {
    }

    public static boolean ehFeriado(LocalDate data) {
        return FIXOS.contains(MonthDay.from(data)) || data.equals(pascoa(data.getYear()).minusDays(2)); // Sexta-feira Santa
    }

    /** Feriados nacionais entre as datas (inclusive). */
    public static Set<LocalDate> entre(LocalDate inicio, LocalDate fim) {
        Set<LocalDate> datas = new HashSet<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            if (ehFeriado(d)) {
                datas.add(d);
            }
        }
        return datas;
    }

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher). */
    static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
