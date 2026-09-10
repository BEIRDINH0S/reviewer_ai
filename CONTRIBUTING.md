# Comment on travaille

Ce fichier décrit le fonctionnement de l'équipe : d'où vient le travail, comment on le prend,
et à quelle condition il entre dans `main`.

---

## Le travail vient des issues, pas du code

**Il n'y a pas de `TODO` dans le code.** Un `TODO` n'a ni auteur fiable, ni date, ni
discussion, ni état ; il survit des mois parce que personne ne le voit, et il n'apparaît nulle
part quand on se demande ce qu'il reste à faire. Le travail restant vit dans les
[issues](../../issues), qui ont tout cela.

Une classe non implémentée le dit clairement — sa Javadoc décrit le contrat attendu, et sa
méthode lève `UnsupportedOperationException`. L'issue correspondante dit qui s'en occupe et
où en est le sujet.

---

## Prendre une tâche

1. Choisir une issue libre et se l'assigner. Une seule à la fois : deux issues ouvertes en
   parallèle par personne, c'est deux branches qui vieillissent au lieu d'une.
2. Créer la branche **depuis `main` à jour** :

   ```bash
   git switch main
   git pull
   git switch -c 12-chargement-archive
   ```

   Nom de branche : **`<numéro d'issue>-<intitulé court en kebab-case>`**. Le numéro suffit à
   retrouver la discussion, l'intitulé suffit à savoir de quoi il s'agit sans l'ouvrir.

3. Commiter par unité de travail, message à l'impératif : `ajoute le chargement d'archive`.
4. Ouvrir une pull request vers `main`, avec `Closes #12` dans la description : l'issue se
   ferme toute seule à la fusion.

---

## Répartition des tâches

Chacun tient une brique, avec ses issues. Ce n'est pas une frontière étanche : on relit le code
des autres, et une brique bloquée se dépanne à deux.

| Personne | Brique | Issues | Ordre conseillé |
|---|---|---|---|
| **1** (lead) | Orchestration et rapport | [#1](../../issues/1) [#2](../../issues/2) [#3](../../issues/3) [#13](../../issues/13) [#14](../../issues/14) | #14, #1, #2, #3, #13 |
| **2** | Chargement de projet et historique | [#6](../../issues/6) [#7](../../issues/7) [#10](../../issues/10) | #7, #6, #10 |
| **3** | Graphe d'appel et critères | [#8](../../issues/8) [#11](../../issues/11) [#17](../../issues/17) | #11, #8, #17 |
| **4** | Contexte | [#9](../../issues/9) [#15](../../issues/15) [#16](../../issues/16) | #9, #16, #15 |
| **5** | Modèle et validation | [#4](../../issues/4) [#5](../../issues/5) [#12](../../issues/12) | #5, #4, #12 |

**Le chemin critique passe par #1, #2, #4 et #5.** Tant que ces quatre issues ne sont pas
terminées, l'outil ne produit aucun rapport, et #13 — un livrable exigé par le sujet — reste
bloquée. Tout le reste peut attendre.

Deux dépendances à connaître avant de commencer : #9 attend #8, et #16 attend #9. La personne 4
a donc intérêt à démarrer par #16 côté protocole pendant que #8 avance.

Les issues à **priorité basse** sont des fonctionnalités que le sujet lui-même dit optionnelles.
Le sujet précise aussi que « les fonctionnalités additionnelles ne compensent pas une
architecture défaillante » : on n'y touche qu'une fois le reste terminé.

Chacun commence par une issue de sa brique, mais **personne ne reste bloqué seul** : au bout
d'une demi-journée sans progrès, on en parle plutôt que de s'entêter.

---

## Pas de branche `develop`

C'est une décision, pas un oubli. Elle mérite d'être justifiée, y compris dans le rapport.

### D'où venait `develop`

Le modèle **git-flow**, décrit par Vincent Driessen en 2010, propose deux branches permanentes
— `main` pour ce qui est publié, `develop` pour ce qui est intégré — plus des branches de
fonctionnalité, de release et de correctif.

Il répondait à un vrai problème, celui du logiciel **versionné et livré par paquets** : un
éditeur qui maintient une 1.4 en production, prépare une 1.5, et doit pouvoir corriger la 1.4
sans embarquer la 1.5. Là, une branche d'intégration séparée de la branche publiée est
indispensable.

Driessen a lui-même ajouté un avertissement en tête de son article en 2020 : ce modèle n'est
pas adapté aux équipes qui livrent en continu, et il voit trop de gens l'adopter par défaut.

### Pourquoi on ne le prend pas

- **`develop` fait intégrer deux fois.** Chaque changement traverse `feature → develop`, puis
  `develop → main`. Les conflits se résolvent deux fois, les régressions se cherchent deux
  fois, et les deux branches divergent entre les fusions.
- **La douleur d'une fusion croît avec l'âge de la branche.** Une branche de deux jours se
  fusionne sans y penser ; une branche de trois semaines occupe une soirée. `develop` encourage
  précisément l'attente : puisqu'il existe un endroit « pas encore publié », rien ne presse.
- **Le filet que `develop` fournissait, la CI le fournit mieux.** `develop` servait d'endroit
  où l'on pouvait casser des choses sans conséquence. On obtient la même garantie — en mieux,
  parce qu'elle est automatique et vérifiée à chaque proposition — en conditionnant l'entrée
  dans `main` au passage des tests.
- **Ce projet n'a qu'une seule version.** Pas de 1.4 à maintenir pendant qu'on écrit la 1.5.
  Une seule livraison : la soutenance. `develop` n'apporterait ici que de la cérémonie.

### Ce qu'on fait à la place

Le modèle **trunk-based**, dans sa variante GitHub Flow : `main` est la seule branche
permanente, elle est toujours en état de marche, et tout le reste est une branche courte issue
de `main` qui y retourne en quelques jours.

```
main ─────●─────────●──────────●──────────●────▶  toujours verte
           \       /  \       /  \       /
            ●─────●    ●─────●    ●─────●         une branche par issue
            12-…       15-…       17-…
```

Règle pratique : **si une branche a plus d'une semaine, c'est que l'issue était trop grosse.**
Il faut la découper.

---

## L'entrée dans `main` est conditionnée

Une pull request ne peut être fusionnée que si :

| Condition | Vérifié par |
|---|---|
| Le projet compile | CI — `docker/mvn.sh package` |
| Tous les tests passent | CI — `docker/mvn.sh test` |
| La Javadoc se génère sans erreur | CI — `mvn javadoc:javadoc` |
| Une autre personne a relu | Revue GitHub, une approbation |
| La branche est à jour avec `main` | GitHub, avant fusion |

C'est ce conditionnement qui rend `develop` inutile : `main` ne peut pas casser, non pas parce
qu'on fait attention, mais parce que rien ne peut y entrer sans avoir été vérifié.

**Fusion en squash**, branche supprimée après coup. Un historique où un commit vaut une issue
se relit ; un historique de trente commits « wip » ne se relit pas, et `git bisect` n'y sert
plus à rien.

### Si la CI est rouge

On corrige sur sa branche, on repousse. On ne fusionne pas « pour débloquer », on ne désactive
pas le test qui gêne. Un test qui échoue dit quelque chose ; si ce qu'il dit est faux, c'est le
test qu'il faut corriger — dans un commit qui explique pourquoi.

---

## Revue de code

Relire le travail d'un autre fait partie du travail. Trois questions suffisent :

1. **Est-ce que je comprends ?** Si non, c'est un problème de code ou de Javadoc, pas de
   lecteur.
2. **Est-ce testé ?** Le cas nominal, mais aussi l'entrée vide, le fichier illisible, la
   réponse absurde du modèle.
3. **Est-ce que ça respecte les conventions ?** Voir [CLAUDE.md](CLAUDE.md).

Une remarque de revue s'écrit sans détour et sans agressivité. On relit du code, pas des gens.

---

## Écrire une issue

Une bonne issue tient en trois parties : **ce qu'on veut**, **comment on saura que c'est
fait**, et **où regarder**. Les issues existantes du dépôt suivent ce format et servent de
modèle.

Découper plutôt qu'accumuler : une issue qui demande plus d'une semaine en cache trois.
