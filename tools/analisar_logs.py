#!/usr/bin/env python3
"""
Analisa os logs que o Farol envia para logs/ no repositório.

Uso:  git pull && python3 tools/analisar_logs.py [pasta_logs] [--dias N]

Mostra, por app: quantas ofertas foram reconhecidas, quantas telas com
"cara de oferta" NÃO foram entendidas (miss), média de R$/km e distribuição
das cores, e imprime exemplos de miss para virarem casos de teste.
"""
import json, sys, glob, os, collections, datetime

def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    pasta = args[0] if args else os.path.join(os.path.dirname(__file__), "..", "logs")
    dias = None
    if "--dias" in sys.argv:
        dias = int(sys.argv[sys.argv.index("--dias") + 1])
    corte = (datetime.date.today() - datetime.timedelta(days=dias)).isoformat() if dias else ""

    eventos = []
    for f in sorted(glob.glob(os.path.join(pasta, "*", "*.jsonl"))):
        if os.path.basename(os.path.dirname(f)) < corte:
            continue
        for linha in open(f, encoding="utf-8"):
            linha = linha.strip()
            if linha:
                try: eventos.append(json.loads(linha))
                except json.JSONDecodeError: pass

    if not eventos:
        print("Nenhum evento encontrado em", os.path.abspath(pasta)); return

    por_app = collections.defaultdict(lambda: {"offer": [], "miss": []})
    for e in eventos:
        if e.get("tipo") in ("offer", "miss"):
            por_app[e.get("app", "?")][e["tipo"]].append(e)

    print(f"{len(eventos)} eventos  ({eventos[0]['t'][:16]} → {eventos[-1]['t'][:16]})\n")
    for app, d in sorted(por_app.items()):
        ofs, ms = d["offer"], d["miss"]
        total = len(ofs) + len(ms)
        taxa = 100 * len(ofs) / total if total else 0
        print(f"== {app}: {len(ofs)} reconhecidas, {len(ms)} não entendidas  ({taxa:.0f}% de acerto)")
        if ofs:
            rkm = [o["r_km"] for o in ofs if o.get("r_km") is not None]
            cores = collections.Counter(o.get("cor") for o in ofs)
            fontes = collections.Counter(o.get("fonte") for o in ofs)
            print(f"   média R$/km {sum(rkm)/len(rkm):.2f} | verde {cores['GREEN']} amarelo {cores['YELLOW']} vermelho {cores['RED']} | fonte {dict(fontes)}")
            suspeitas = [o for o in ofs if o.get("r_km", 0) > 8 or o.get("r_km", 9) < 0.5 or o.get("km", 0) > 80]
            for o in suspeitas[:3]:
                print(f"   ⚠ valor suspeito: R$ {o['preco']} / {o['km']} km  ←  {o['texto'][:160]}")
        vistos = set()
        for m in ms:
            chave = "".join(c for c in m["texto"] if not c.isdigit())[:120]
            if chave in vistos: continue
            vistos.add(chave)
            print(f"   ✗ [{m.get('fonte')}] {m['texto'][:240]}")
            if len(vistos) >= 5: break
        print()

if __name__ == "__main__":
    main()
