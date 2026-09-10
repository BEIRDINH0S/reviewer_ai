# Rapport — plan et points à ne pas oublier

Le rapport est rédigé sur Overleaf. Ce fichier sert d'aide-mémoire : ce qu'il faut y mettre, et
où trouver la matière dans le dépôt.

---

## Contraintes de forme

- [ ] **Numéro étudiant, pas le nom** — pour chaque membre de l'équipe.
- [ ] Table des matières, numérotation des sections, figures légendées.
- [ ] Références aux versions des outils (voir le tableau du `README.md`).

---

## Plan proposé

### 1. Présentation

Le problème, ce que fait l'outil, ce qu'il ne fait pas. Une capture de l'interface graphique et
un extrait de rapport produit.

### 2. Conception

Reprendre [DESIGN.md](DESIGN.md) : schéma MVC, tableau SOLID, tableau des patrons, et surtout
les **décisions et alternatives écartées** — c'est la partie qui montre qu'on a réfléchi plutôt
que subi.

Ne pas oublier la section « points de couplage assumés » : le sujet pose la question
directement, et nommer ses propres faiblesses vaut mieux que de laisser le correcteur les
trouver.

Le sujet prévient qu'un patron ajouté pour gonfler la liste sera pénalisé. Pour chaque patron
présenté, savoir répondre à deux questions : **quel problème réel il résout ici**, et **ce qui
casserait si on l'enlevait**.

### 3. Réalisation

Les six briques, la répartition dans l'équipe, et ce qui a été difficile. Mentionner
concrètement : la stratégie de sélection des extraits sous budget de jetons, les faux positifs
du modèle, et la résolution de symboles sans classpath pour le graphe d'appel.

Le sujet demande explicitement la **répartition des tâches entre les membres**. Elle se lit
dans les issues du dépôt : chacune porte son assignation, et l'historique git montre qui a
fait quoi. Le rapport en donne la synthèse.

### 3 bis. Organisation de l'équipe

Une section courte, mais qui rapporte : le fonctionnement en branche par issue depuis `main`,
sans `develop`, et le conditionnement de la fusion au passage de la CI. La justification
complète est dans `CONTRIBUTING.md` — reprendre l'essentiel : git-flow répond au problème du
logiciel versionné livré par paquets, que ce projet n'a pas, et la garantie qu'apportait
`develop` est fournie en mieux par une CI bloquante.

### 4. Qualité et tests

Stratégie de test, ce qui est couvert et ce qui ne l'est pas, intégration continue.
Expliquer pourquoi les tests n'ont besoin ni d'Ollama ni d'un dépôt git préexistant.

### 5. Sécurité

Le code analysé est non fiable. Reprendre le tableau des risques du `README.md`, et expliquer
la conséquence principale : refuser de compiler le code analysé, ce qui interdit d'utiliser
SpotBugs ou Error Prone, et impose l'analyse sans classpath.

### 6. Outils utilisés

À citer explicitement, avec ce que chacun a servi à faire :

| Outil | Usage |
|---|---|
| **Docker** | Isolation de l'analyse : conteneur jetable, sans réseau, dépôt monté en lecture seule |
| **Ollama** | Exécution du modèle en local, aucune donnée envoyée à l'extérieur |
| **Claude Code** | Assistance au développement (voir section suivante) |
| **Obsidian** | Notes et suivi du travail |
| **Overleaf** | Rédaction de ce rapport |
| **GitHub Actions** | Tests automatiques et publication de la documentation |
| **IntelliJ IDEA / VS Code** | Développement |

### 7. Utilisation de l'IA dans le projet

Section attendue explicitement. Être précis et honnête :

- ce qui a été **généré** par l'IA (structure du projet, squelettes de classes, documentation) ;
- ce qui a été **écrit à la main** ;
- ce qui a été **corrigé** après génération, et pourquoi — les erreurs valent la peine d'être
  citées, elles montrent qu'on a relu ;
- comment le résultat a été **vérifié** (tests, relecture, exécution) ;
- la distinction entre l'IA comme *outil de développement* (Claude Code) et l'IA comme *sujet
  du projet* (le modèle local qui relit le code).

### 8. Bilan

Ce qui marche, ce qui ne marche pas, ce qu'on ferait avec plus de temps (fin de
[DESIGN.md](DESIGN.md)).

---

## Matière disponible dans le dépôt

| Besoin | Où |
|---|---|
| Schémas d'architecture | `README.md`, `DESIGN.md` (blocs mermaid) |
| Justification des choix | `DESIGN.md` |
| Conventions de code | `CLAUDE.md` |
| Détail des briques | Javadoc publiée sur GitHub Pages |
| Historique du travail | `git log` |


---

## Les neuf questions auxquelles le rapport doit répondre

Le sujet les liste explicitement. Le tableau de correspondance est à la fin de
[DESIGN.md](DESIGN.md) ; le rapport doit y répondre en toutes lettres, pas seulement renvoyer
au code.

1. Quelles sont les responsabilités principales du système ?
2. Comment les avez-vous séparées ?
3. Quels patrons de conception avez-vous utilisés, et pourquoi ?
4. Comment remplacer le modèle actuel par un autre ?
5. Comment ajouter un critère d'évaluation ?
6. Comment tester l'application sans appeler réellement un modèle ?
7. Comment gérez-vous une réponse invalide du modèle ?
8. Comment empêchez-vous un projet évalué d'endommager la machine ?
9. Quels sont aujourd'hui les principaux points de couplage de votre architecture ?

---

## Livrables à ne pas oublier

- [ ] Le code source, les tests, les scripts, le ou les Dockerfile, le README.
- [ ] **Un `evaluation.tex` réellement produit par le programme**, et son PDF si possible.
      C'est un livrable à part entière, pas une capture d'écran.
- [ ] Le rapport technique lui-même.
- [ ] La description de l'usage de l'IA pendant le développement : ce qui a été engendré, ce
      qui a été repris à la main, ce qui a été corrigé et pourquoi, comment le résultat a été
      vérifié.
