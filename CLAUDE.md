# AI Reviewer — conventions du projet

Ce fichier est lu par Claude Code à chaque session, et sert aussi de référence à l'équipe.
Il aligne le projet sur le cours de Génie Logiciel de Christopher Leturc (Université Côte
d'Azur), *Introduction aux Patrons de Conception*.

---

## Le projet en deux phrases

Un outil qui évalue un projet Java selon des critères notés et produit un rapport LaTeX. Au
lieu d'envoyer tout le projet à un modèle de langage, chaque critère reçoit une sélection
d'extraits choisie pour lui, sous un budget de jetons, ce qui permet d'utiliser un petit modèle
local.

C'est avant tout un projet de **qualité de code**. L'IA est le sujet, pas l'excuse.

---

## Ce que le cours attend

Le cours couvre quatre familles de patrons :

| Famille | Rôle | Patrons |
|---|---|---|
| **Création** | Créer des objets avec souplesse | Abstract Factory, Factory, Builder, Prototype, Singleton |
| **Comportementaux** | Répartir les responsabilités et les algorithmes | Chain of Responsibility, Command, Interpreter, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor |
| **Structurels** | Assembler classes et objets en structures plus vastes | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| **Hybrides** | Combinaisons, niveau architectural | **MVC**, Pluggable Factory, Visitor, Multicast |

Trois règles de méthode tirées du cours :

1. **Documenter chaque patron avec les quatre rubriques du cours** : Nom, Problème traité,
   Solution, Remarques. C'est le format employé dans tous les supports, donc celui attendu dans
   le rapport et dans la Javadoc des classes concernées.
2. **UML avant le code.** Chaque exercice du cours demande d'abord le diagramme, ensuite
   l'implémentation. Le rapport doit contenir les diagrammes de classes.
3. **Le vocabulaire compte.** Un patron nommé dans le code doit correspondre à sa définition.
   Écrire « Factory » sur une classe qui n'en est pas une est pire que ne rien écrire.

---

## Patrons utilisés dans ce projet

Chaque classe qui implémente un patron le déclare dans sa Javadoc, au format du cours.

| Patron | Famille | Où | Problème résolu ici |
|---|---|---|---|
| **MVC** | Hybride | architecture globale | Deux interfaces utilisateur, une seule logique métier |
| **Stratégie** | Comportemental | `Criterion`, `ContextBuilder`, `ProjectLoader`, `FileSelector`, `ReportWriter`, `LlmProvider`, `FindingVerifier` | Changer d'algorithme sans toucher à l'appelant |
| **Patron de méthode** | Comportemental | `AbstractLlmCriterion` | Six critères, un seul déroulé, deux étapes variables |
| **Décorateur** | Structurel | `RetryingLlmProvider`, `CountingLlmProvider` | Résilience et traçabilité sans modifier le fournisseur |
| **Observateur** | Comportemental | `ProgressListener` | Le service informe les vues sans les connaître |
| **Composite** | Structurel | `CompositeFindingVerifier`, `CompositeFileSelector` | Un groupe de règles s'utilise comme une règle seule |
| **Adaptateur** | Structurel | `OllamaLlmProvider` | Isoler tout ce qui est propre à LangChain4j |
| **Fabrique** | Création | `EvaluationServiceFactory`, `ProjectLoaderFactory` | Le câblage tient à un seul endroit |
| **Monteur** | Création | `EvaluationConfig.Builder` | Construire un objet à quinze réglages sans liste illisible |
| **Façade** | Structurel | `EvaluationService` | Une porte d'entrée unique sur les six briques |
| **Objet nul** | — | `ProgressListener.noop()`, `AnalysisHistory.none()`, `PdfCompiler.none()` | Supprimer les tests de nullité chez l'appelant |

### Écarts assumés par rapport au cours

À signaler dans le rapport plutôt qu'à cacher — les justifier montre qu'on a compris le patron.

- **Stratégie et Observateur sont des interfaces, pas des classes abstraites.** Le cours
  présente `Strategy` comme une classe abstraite portant un attribut `context`, et `Observable`
  comme une classe abstraite gérant sa liste d'observateurs. Ici les stratégies n'ont pas
  d'état à partager et Java ne permet qu'un seul héritage : une interface laisse
  `EvaluationWebView` être à la fois une vue et un observateur. L'intention est respectée, la
  forme diffère.
- **`AbstractLlmCriterion` est bien une classe abstraite**, elle : c'est le seul endroit où
  l'état et le déroulé sont réellement partagés. La différence de traitement avec les
  stratégies n'est pas une incohérence, c'est le critère de choix lui-même.
- **`EvaluationServiceFactory` est une fabrique statique**, pas la `Factory` du cours avec sa
  méthode `build()` publique et sa méthode `howToBuild()` protégée. Il n'y a qu'une façon de
  construire le service ; introduire une hiérarchie de fabriques pour une seule variante serait
  de la complexité gratuite.
- **Pas de Singleton.** Le cours l'enseigne, mais un état global rend les tests dépendants les
  uns des autres. Les objets uniques du projet sont créés une fois dans `Main` et passés par
  constructeur — même résultat, sans le couplage.

### Pistes si on veut enrichir la copie

- **Visiteur** : JavaParser repose dessus (`VoidVisitorAdapter`). La brique Graphe l'utilisera
  donc naturellement — c'est le lien le plus direct entre le cours et le code.
- **Chaîne de responsabilité** : les vérificateurs pourraient être une vraie chaîne à
  successeurs plutôt qu'un composite.
- **Mémento** : comparer deux évaluations successives du même projet, ce que le sujet liste en
  fonctionnalité optionnelle. L'historique en fournit déjà la matière.
- **Proxy** : mettre un cache devant `LlmProvider` — un troisième décorateur, qui éviterait de
  repayer un appel identique.

## Architecture

Découpage Modèle-Vue-Contrôleur, le patron hybride du cours.

| Couche | Packages | Rôle |
|---|---|---|
| Modèle | `model`, `service`, `project`, `criteria`, `context`, `llm`, `verify`, `report`, `history`, `diff`, `graph` | Données et logique métier |
| Vue | `view.cli`, `view.web` | Affichage, rien d'autre |
| Contrôleur | `controller` | Fait le lien entre les deux |

Sens des dépendances : **Vue → Contrôleur → Service → Briques → Modèle**. Jamais l'inverse.
Le modèle ne connaît personne, la vue ne connaît pas les briques.

---

## Règles d'écriture

**Une classe, une raison de changer.** Si on hésite à nommer une classe, c'est souvent qu'elle
en fait deux.

**Dépendre d'interfaces, pas d'implémentations.** Une classe reçoit ses collaborateurs par son
constructeur. Aucun `new` d'une implémentation concrète en dehors de `EvaluationServiceFactory`,
`ProjectLoaderFactory` et `Main`.

**Pas de variable globale ni d'état statique modifiable.**

**Ajouter sans modifier.** Un nouveau critère est une classe qui implémente `Criterion`. Un
nouveau format de rapport est une implémentation de `ReportWriter`. Un nouveau fournisseur de
modèle est une implémentation de `LlmProvider`. Une nouvelle provenance de projet est une
implémentation de `ProjectLoader`. Aucune de ces additions ne touche au code existant : elles
ajoutent une classe et une ligne dans une fabrique.

**Immuabilité par défaut.** Les données sont des `record`, les champs sont `final`, les
collections renvoyées sont non modifiables.

**Pas de `null` qui circule.** Utiliser `Optional` en valeur de retour, ou un objet nul
(`ProgressListener.noop()`, `AnalysisHistory.none()`, `PdfCompiler.none()`,
`CodeGraph.empty()`, `StubLlmProvider.silent()`).

**Échouer tôt.** `Objects.requireNonNull` dans les constructeurs, validation dans les compacts
de `record`.

**Une exception sur un critère ne fait pas tomber l'évaluation.** On renvoie un
`CriterionResult.failed`, on signale via `ProgressListener.onWarning`, et on passe au suivant.
Le sujet appelle cela la récupération partielle ; c'est une exigence, pas un confort.

**Méthodes courtes, un seul niveau d'abstraction.**

---

## Commentaires et documentation

La documentation est lue par des humains. Elle doit être **claire, courte, en français**.

- Une Javadoc sur chaque classe et chaque méthode publique : ce qu'elle fait, et pourquoi elle
  existe.
- Une classe qui implémente un patron le dit, au format du cours (Nom, Problème traité,
  Solution, Remarques).
- Un `package-info.java` par package.
- Les commentaires dans le corps du code expliquent **pourquoi**, jamais **quoi**.
- Pas de commentaire qui paraphrase la ligne suivante, pas de bloc décoratif.
- **Pas de `TODO` dans le code.** Le travail restant vit dans les issues du dépôt, qui ont un
  auteur, une date, un état et une discussion — ce qu'un `TODO` n'a pas. Une classe non
  implémentée le dit par sa Javadoc et lève `UnsupportedOperationException`.

La Javadoc est publiée sur GitHub Pages à chaque push sur `main`.

---

## Tests

JUnit 5. Un test répond à trois questions : quelle situation, quelle action, quel résultat.

- Nom de méthode descriptif, plus un `@DisplayName` en français.
- Un comportement par test.
- Aucune dépendance à l'environnement : ni Ollama, ni projet préexistant, ni réseau. Les
  projets de test se créent dans un `@TempDir`.
- Le pipeline complet doit tourner sans modèle, via `StubLlmProvider`. C'est une exigence
  explicite du sujet : « l'architecture doit permettre de tester sans appeler un LLM à chaque
  test ».
- Les cas limites comptent autant que le cas nominal : entrée vide, fichier illisible, réponse
  du modèle absurde.

Lancer : `docker/mvn.sh test` (rien à installer) ou `mvn test`.

---

## Sécurité

Le code analysé est **non fiable**. On le lit, on ne l'exécute jamais.

- Jamais de `mvn`, `gradle` ou script du projet évalué. Le parsing seul.
- Le modèle ne dispose d'aucun outil : il reçoit du texte, il renvoie du texte.
- Sa réponse est validée (`JsonCriterionResponseParser`, puis les `FindingVerifier`) et enfin
  échappée selon le format de sortie : `LatexEscaper` pour le rapport — le plus critique, LaTeX
  étant exécutable —, `MarkdownEscaper`, et `textContent` pour la page web.
- La lecture des fichiers passe par `SafeFiles` : pas de lien symbolique, pas de chemin hors du
  projet, taille limitée, et à l'extraction d'archive, ni chemin qui s'échappe ni contenu
  démesuré.
- Le serveur web et Ollama n'écoutent que sur `127.0.0.1`.

---

## Conventions concrètes

- Java 25, Maven.
- Indentation 4 espaces, lignes ≤ 120 caractères.
- Code, noms de classes et de méthodes en **anglais**. Commentaires, Javadoc et messages
  affichés en **français**.
- **Chemins de fichiers** : toujours passer par `SafeFiles.toRepoRelative`, jamais par
  `Path.toString()`. Windows sépare avec des `\` et JGit avec des `/`.
- L'équipe travaille sur Windows et macOS. Les scripts sont en `.sh`, lancés depuis Git Bash
  sous Windows, et `.gitattributes` garantit qu'ils restent en LF.
- Un commit par unité de travail, message à l'impératif : `ajoute le parseur de réponses`.
- **Une branche par issue**, nommée `<numéro>-<intitulé-court>`, partant de `main`. Pas de
  branche `develop` : les raisons sont expliquées dans [CONTRIBUTING.md](CONTRIBUTING.md), et
  méritent d'être reprises dans le rapport.
- Une pull request ne peut entrer dans `main` que si la CI est verte et qu'une autre personne
  a relu.

---

## Outils utilisés

Docker (build et isolation de l'analyse), Ollama (modèle local), Claude Code (assistance au
développement), Obsidian (notes), Overleaf (rapport), GitHub Actions (intégration continue et
publication de la documentation).

L'usage de l'IA doit être décrit dans le rapport : ce qui a été généré, ce qui a été repris à
la main, ce qui a été corrigé et pourquoi, et comment le résultat a été vérifié.
