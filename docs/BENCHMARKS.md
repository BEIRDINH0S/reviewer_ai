# Benchmark d'impact du budget de contexte

## Objectif
Mesurer l'impact de la taille du budget de contexte (`maxContextTokens`) et du nombre de fichiers par critère (`maxFilesPerCriterion`) sur la qualité de l'évaluation, la stabilité des notes et les performances d'exécution du modèle `qwen2.5-coder:7b`.

## Tableau des résultats expérimentaux

| Configuration | Budget Tokens | Fichiers / Critère | Note globale | Temps total | Appels LLM | Observation majeure |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Run 1 (Restreint)** | 2 000 | 5 | 14,2 / 20 | 1 717 s<br><span style="white-space:nowrap">(~28 min)</span> | 6 | Sous-évaluation globale due au manque de visibilité contextuelle. |
| **Run 2 (Équilibré)** | 6 000 | 12 | **17,2 / 20** | **805 s**<br><span style="white-space:nowrap">**(~13 min)**</span> | 6 | **Meilleur compromis** avec note cohérente et temps d'exécution minimal. |
| **Run 3 (Surchargé)** | 12 000 | 25 | 10,8 / 20 | 857 s<br><span style="white-space:nowrap">(~14 min)</span> | 6 | Effet *Lost in the Middle* et chute brutale des notes sur SOLID, Sécurité et Patrons. |

---

## Analyse des mesures

1. **Phénomène de surcharge du contexte (*Lost in the Middle*) :**
    - À **2 000 tokens**, le modèle ne dispose pas d'assez d'éléments pour valider l'architecture globale, entraînant une note prudente mais sous-évaluée (14,2/20).

    <br>

    - À **6 000 tokens**, le modèle capture correctement les dépendances clés sans saturation. La note monte à **17,2/20** et le temps d'exécution est divisé par deux (805 s).

   <br>

    - À **12 000 tokens**, la qualité s'effondre (10,8/20). Injecter 25 fichiers par critère noie les informations critiques. Le modèle attribue **0/10** sur plusieurs critères complexes (SOLID, Patrons de conception, Sécurité), incapable d'isoler les invariants pertinents au milieu du bruit.

   <br>

2. **Variabilité et stabilité :**
    - Le nombre d'appels LLM reste stable (6 appels au total pour couvrir les 10 critères).
    - Le temps d'exécution n'est pas linéaire : la phase de pré-chargement et la complexité de traitement par prompt génèrent des écarts importants lorsque le contexte est mal dimensionné.

---

## Recommandation pour `EvaluationConfig.java`

Les mesures contredisent l'hypothèse selon laquelle « plus de contexte donne un meilleur rapport ».

- **Réglage recommandé par défaut :**
    - `maxContextTokens = 6000`
    - `maxFilesPerCriterion = 12`

  <br>
- **Justification :** Cette configuration offre à la fois la meilleure fidélité d'évaluation (17,2/20) et l'exécution la plus rapide (805 secondes).

---

## Limites de l'étude (Section pour le rapport)
* **Périmètre des projets :** Les tests ont été menés sur la base de code courante (~9 200 lignes, 161 fichiers). Des projets de taille industrielle > 100k lignes de codes, pourraient nécessiter un découpage par sous-modules plutôt qu'une augmentation du budget de tokens.

  <br>
* **Dépendance au modèle :** Ces conclusions sont spécifiques à `qwen2.5-coder:7b`. Des modèles avec des fenêtres d'attention plus grandes ou mieux entraînées sur du long contexte (ex: Claude 3.5, Gemini 1.5) pourraient réagir différemment à la configuration 12 000 tokens.