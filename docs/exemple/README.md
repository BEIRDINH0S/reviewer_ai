# Rapport d'exemple

Ce rapport a été **réellement produit par l'application**, en évaluant ce dépôt lui-même. Il
n'a pas été retouché à la main : les remarques que l'outil nous adresse, y compris les
sévères, sont conservées telles quelles — ce sont d'excellentes questions de soutenance.

## Ce qui a produit ce rapport

| | |
|---|---|
| Projet évalué | ce dépôt (`reviewer_ai`) |
| Révision | `18d93a0` |
| Date | 10/09/2026 |
| Modèle | `qwen2.5-coder:1.5b` (petit modèle local, ~1 Go) |
| Configuration | budget 6000 jetons · 12 fichiers/critère · confiance ≥ 0.50 · 3 tentatives |
| Appels au modèle | 6, en 1 min 02 s |
| Note globale | 11,8/20 |

Le choix d'un petit modèle est assumé : il tient sur une machine modeste et rend la
démonstration reproductible en une minute. Un modèle plus grand noterait sans doute mieux les
critères d'architecture ; l'écart est précisément une matière de rapport (voir l'issue #16).

## Commande exacte

Le serveur Ollama tourne sur la machine hôte, avec le modèle déjà tiré
(`ollama pull qwen2.5-coder:1.5b`) :

```bash
MODEL=qwen2.5-coder:1.5b MODE=trusted docker/run-analysis.sh .
```

Le rapport atterrit dans `out/evaluation.tex` ; c'est ce fichier qui a été copié ici.

## Produire le PDF

Le `.tex` est autonome (classe `article`, `longtable`, `hyperref`). Le compiler avec n'importe
quelle distribution LaTeX :

```bash
pdflatex evaluation.tex
```

ou, sans rien installer, en le déposant sur [Overleaf](https://overleaf.com).
