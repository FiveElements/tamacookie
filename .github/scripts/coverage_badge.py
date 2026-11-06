#!/usr/bin/env python3
"""Badge de couverture (SVG, style « flat » de shields.io) à partir d'un ou plusieurs rapports JaCoCo XML.

Aucun service externe : la CI publie le SVG sur la branche dédiée `badges` (main est protégée),
lu par le README via raw.githubusercontent.com. Taux = lignes couvertes / lignes, en **sommant** les
compteurs LINE globaux de chaque rapport (un par module : `:app` par AGP, `:core` par le plugin `jacoco`).

Usage : coverage_badge.py <report.xml> [<report.xml>...] <sortie.svg> [--summary <fichier>]
Affiche le taux total (ex. « 49.5 ») sur la sortie standard.
"""
import os
import sys
import xml.etree.ElementTree as ET


def line_counter(report: str) -> tuple[int, int]:
    """(lignes manquées, lignes couvertes) du compteur LINE global du rapport."""
    root = ET.parse(report).getroot()
    for c in root.findall("counter"):  # compteurs globaux (enfants directs de <report>)
        if c.get("type") == "LINE":
            return int(c.get("missed")), int(c.get("covered"))
    raise SystemExit(f"{report} : pas de compteur LINE")


def percent(missed: int, covered: int) -> float:
    return 100.0 * covered / (missed + covered) if missed + covered else 0.0


def color(pct: float) -> str:
    # Mêmes paliers que les badges de couverture courants (rouge → vert)
    for limit, c in ((90, "#4c1"), (80, "#97ca00"), (70, "#a4a61d"), (60, "#dfb317"), (50, "#fe7d37")):
        if pct >= limit:
            return c
    return "#e05d44"


def text_width(s: str) -> int:
    # Approximation de Verdana 11 px (largeur moyenne par caractère), suffisante pour un badge
    narrow = set("ilI1.,:;' ")
    return int(sum(4 if ch in narrow else 7 for ch in s)) + 10


def svg(label: str, value: str, fill: str) -> str:
    lw, vw = text_width(label), text_width(value)
    w = lw + vw
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="20" role="img" aria-label="{label}: {value}">
<title>{label}: {value}</title>
<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
<clipPath id="r"><rect width="{w}" height="20" rx="3" fill="#fff"/></clipPath>
<g clip-path="url(#r)"><rect width="{lw}" height="20" fill="#555"/><rect x="{lw}" width="{vw}" height="20" fill="{fill}"/><rect width="{w}" height="20" fill="url(#s)"/></g>
<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="11">
<text x="{lw / 2}" y="15" fill="#010101" fill-opacity=".3">{label}</text><text x="{lw / 2}" y="14">{label}</text>
<text x="{lw + vw / 2}" y="15" fill="#010101" fill-opacity=".3">{value}</text><text x="{lw + vw / 2}" y="14">{value}</text>
</g>
</svg>
"""


def fr(pct: float) -> str:
    return f"{pct:.1f} %".replace(".", ",")


def main() -> None:
    args = sys.argv[1:]
    summary = None
    if "--summary" in args:
        i = args.index("--summary")
        summary = args[i + 1]
        del args[i:i + 2]
    if len(args) < 2:
        raise SystemExit(__doc__)
    reports, out = args[:-1], args[-1]
    counters = [(r, *line_counter(r)) for r in reports]
    missed = sum(m for _, m, _ in counters)
    covered = sum(c for _, _, c in counters)
    pct = percent(missed, covered)
    value = fr(pct)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(svg("couverture", value, color(pct)))
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"### Couverture des tests JVM : {value} des lignes\n\n")
            if len(counters) > 1:
                f.write("| Rapport | Lignes couvertes | Taux |\n|---|---:|---:|\n")
                for r, m, c in counters:
                    f.write(f"| `{os.path.relpath(r)}` | {c} / {m + c} | {fr(percent(m, c))} |\n")
                f.write(f"| **Total** | **{covered} / {missed + covered}** | **{value}** |\n")
    print(f"{pct:.1f}")


if __name__ == "__main__":
    main()
