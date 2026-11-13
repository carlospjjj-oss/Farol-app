# Ofertas reais como teste (OfertasReaisTest)

`app/src/test/resources/golden/ofertas_reais.tsv` guarda ofertas e telas reais dos logs, **anonimizadas**:
qualquer palavra que não seja do vocabulário do app (nomes de rua, pessoa, loja) vira `xxx`. Um caso só entra
se o texto anonimizado for lido exatamente igual ao original.

Ao corrigir o leitor de propósito, gere de novo a partir dos logs (feito pelo Claude na sessão):
1. juntar as ofertas e os "miss" do repositório de logs;
2. anonimizar e conferir original == anonimizado com `GoldenSnap.kt`;
3. gravar o resultado atual como esperado.
