#!/usr/bin/env python3
"""Badge de couverture (SVG, style « flat » de shields.io) à partir d'un rapport JaCoCo XML.

Aucun service externe : la CI publie le SVG sur la branche dédiée `badges` (main est protégée),
lu par le README via raw.githubusercontent.com. Taux = lignes couvertes / lignes (compteur LINE global du rapport).

Usage : coverage_badge.py <report.xml> <sortie.svg> [--summary <fichier>]
Affiche le taux (ex. « 41.5 ») sur la sortie standard.
"""
import sys
import xml.etree.ElementTree as ET


def line_coverage(report: str) -> float:
    root = ET.parse(report).getroot()
    for c in root.findall("counter"):  # compteurs globaux (enfants directs de <report>)
        if c.get("type") == "LINE":
            missed, covered = int(c.get("missed")), int(c.get("covered"))
            return 100.0 * covered / (missed + covered) if missed + covered else 0.0
    raise SystemExit(f"{report} : pas de compteur LINE")


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


def main() -> None:
    args = sys.argv[1:]
    if len(args) < 2:
        raise SystemExit(__doc__)
    report, out = args[0], args[1]
    pct = line_coverage(report)
    value = f"{pct:.1f} %".replace(".", ",")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(svg("couverture", value, color(pct)))
    if "--summary" in args:
        with open(args[args.index("--summary") + 1], "a", encoding="utf-8") as f:
            f.write(f"### Couverture des tests JVM : {value} des lignes\n")
    print(f"{pct:.1f}")


if __name__ == "__main__":
    main()
