#!/usr/bin/env python3
"""Acha nos logs (cópia privada) telas reais de ganho/histórico/espera que ainda não estão no banco de testes.

Uso:  python3 tools/telas_golden.py <pasta com logs/AAAA-MM-DD/*.jsonl>
Imprime linhas no formato de app/src/test/resources/golden/telas_reais.tsv com "esperado" = "?".
Confira cada uma (o texto já vem mascarado do servidor), preencha o esperado e cole no arquivo.
"""
import glob, json, os, re, sys

PADROES = {
    "pedido": r"Pedido conclu[ií]do",
    "painel": r"^Painel \|",
    "historico_dia": r"Ganhos totais",
    "lancamentos": r"Lan[cç]amentos",
    "pedido_oferta": r"c[oó]digo de verifica",
    "espera": r"Pedido pronto em cerca|esperando h",
    "saldo": r"(^|\| )Saldo \|",
}
APPS = {"sankuai": "Keeta", "app99": "99", "ifood": "iFood", "ubercab": "Uber", "indriver": "inDrive"}

def main(root):
    golden = os.path.join(os.path.dirname(__file__), "..", "app/src/test/resources/golden/telas_reais.tsv")
    have = set(l.split("\t", 3)[3].strip() for l in open(golden, encoding="utf-8") if l.strip() and not l.startswith("#"))
    seen = set()
    for f in sorted(glob.glob(os.path.join(root, "**", "*.jsonl"), recursive=True)):
        for line in open(f, encoding="utf-8"):
            try: e = json.loads(line)
            except Exception: continue
            t = (e.get("texto") or "").replace("\t", " ").strip()
            if e.get("tipo") != "tela" or not t or t in have: continue
            app = next((v for k, v in APPS.items() if k in (e.get("app") or "")), None)
            if not app: continue
            for k, p in PADROES.items():
                if re.search(p, t) and (k, t[:80]) not in seen:
                    seen.add((k, t[:80])); print(f"{app}\t{k}\t?\t{t}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "logs")
