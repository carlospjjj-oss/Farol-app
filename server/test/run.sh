#!/bin/bash
# Roda os testes do servidor (Node 22+, SQLite em memória simulando o D1). Falha se algum "✘ FALHOU".
cd "$(dirname "$0")"
fail=0
for t in *.test.mjs; do
  out=$(timeout 60 node --import ./nofetch.mjs "$t" 2>&1); code=$?
  n=$(echo "$out" | grep -c "✘")
  echo "$t: $(echo "$out" | grep -c "✔") ok, $n falhas (saída $code)"
  if [ "$n" -gt 0 ] || [ $code -ne 0 ]; then echo "$out" | grep -E "✘|Error" | head -20; fail=1; fi
done
exit $fail
