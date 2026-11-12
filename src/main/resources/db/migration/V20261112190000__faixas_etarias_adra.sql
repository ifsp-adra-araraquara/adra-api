-- E-mail da ADRA de 11/11: as faixas passam a ser 6 a 10, 11 a 13 e 14 a 17.
-- Os grupos cadastrados com as faixas antigas da lista do sistema mudam de nome;
-- textos digitados de outro jeito ficam como estão (a tela mostra como "antiga").
UPDATE adra.turma SET faixa_etaria = '6 a 10 anos'  WHERE faixa_etaria = '6–9 anos';
UPDATE adra.turma SET faixa_etaria = '11 a 13 anos' WHERE faixa_etaria = '10–13 anos';
UPDATE adra.turma SET faixa_etaria = '14 a 17 anos' WHERE faixa_etaria = '14–17 anos';
