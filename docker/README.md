# Utilisation avec Docker

Docker évite d'installer Maven et un JDK sur la machine. Il sert aussi à isoler l'analyse
quand le code relu ne vient pas de l'équipe.

## Développer sans rien installer

```bash
docker/mvn.sh test              # tests unitaires
docker/mvn.sh package           # jar dans target/
docker/mvn.sh javadoc:javadoc   # documentation dans target/reports/apidocs
```

Le cache Maven est conservé dans un volume Docker : seul le premier appel télécharge les
dépendances. Les fichiers produits dans `target/` appartiennent bien à l'utilisateur courant.

**Limite** : le jar construit ainsi embarque les bibliothèques natives JavaFX de Linux.
Il fonctionne en ligne de commande, mais pas pour l'interface graphique sur macOS. Pour la
fenêtre JavaFX, construire sur la machine avec un JDK 25 local.

## Sous Windows

Lancer les scripts depuis **Git Bash**, installé avec Git for Windows. Ils ne fonctionnent ni
en `cmd` ni en PowerShell.

```bash
docker/mvn.sh test
```

Les scripts gèrent déjà les deux particularités de Windows : la conversion des chemins pour
Docker Desktop (`C:/Users/...` plutôt que `/c/Users/...`) et la réécriture automatique des
chemins par MSYS.

Si tu préfères PowerShell, la commande équivalente s'écrit sur une ligne :

```powershell
docker run --rm -v "${PWD}:/app" -w /app -v ai-reviewer-m2:/root/.m2 `
  maven:3.9-eclipse-temurin-25 mvn --batch-mode test
```

Deux points à connaître :

- **Fins de ligne.** Le fichier `.gitattributes` force les `.sh` en LF. Sans lui, Git for
  Windows les convertirait en CRLF et le conteneur répondrait
  `bad interpreter: /usr/bin/env bash^M`. Ne pas le supprimer.
- **Accents dans la console.** Les messages de l'outil sont en français. Si la console Windows
  les affiche mal, lancer `chcp 65001` avant, ou ajouter `-Dstdout.encoding=UTF-8` à la
  commande `java`. Le problème ne se pose pas dans le conteneur.

## Lancer une analyse

```bash
# Code venant de l'équipe : Ollama tourne sur la machine
MODE=trusted docker/run-analysis.sh /chemin/du/depot abc123 def456

# Code venant de l'extérieur : aucun accès réseau
docker/run-analysis.sh /chemin/du/depot abc123 def456
```

Le rapport arrive dans `out/report.md`.

## Ce que fait le mode isolé

| Option | Pourquoi |
|---|---|
| `--network reviewer-net` (interne) | Pas d'accès à Internet |
| `--read-only --tmpfs /tmp` | Le système de fichiers du conteneur n'est pas modifiable |
| `-v .../repo:ro` | Le dépôt analysé est monté en lecture seule |
| `--user 1000:1000` | Pas de privilèges root |
| `--cap-drop ALL --security-opt no-new-privileges` | Aucune capacité Linux, pas d'élévation possible |
| `--memory 4g --cpus 2 --pids-limit 256` | Un fichier piégé ne peut pas saturer la machine |
| `--rm` | Le conteneur est détruit après chaque analyse |

L'image finale ne contient ni Maven ni Gradle : même en cas d'erreur de programmation, le
`pom.xml` du dépôt analysé ne peut pas être exécuté.

## Ollama

**Sur macOS, installer Ollama sur la machine, pas dans Docker.** Un conteneur Linux n'a pas
accès au GPU d'un Mac : le modèle tournerait sur le processeur, plusieurs fois plus lentement.

```bash
brew install ollama
ollama serve &
ollama pull qwen2.5-coder:7b
```

C'est le mode `trusted` : le conteneur d'analyse joint Ollama via `host.docker.internal`.

Pour le mode isolé, il faut au contraire un Ollama en conteneur, attaché au réseau interne.
Télécharger le modèle **avant** de le rattacher, car ce réseau n'a pas d'accès à Internet :

```bash
docker run -d --name ollama -v ollama:/root/.ollama ollama/ollama
docker exec ollama ollama pull qwen2.5-coder:7b
docker network connect reviewer-net ollama
```

Attention à la mémoire allouée à Docker Desktop : un modèle 7B en demande environ 5 Go, et
l'analyse elle-même 4 Go. Avec les 4 Go alloués par défaut, il faut soit augmenter la limite
dans les réglages de Docker Desktop, soit choisir un modèle plus petit
(`qwen2.5-coder:1.5b`), soit garder Ollama hors du conteneur.

L'API d'Ollama n'a **aucune authentification**. Ne jamais l'exposer en dehors de la machine :
sur le réseau local, restreindre par pare-feu ou passer par un proxy authentifié ; sur
Internet, jamais.

## Limites de l'isolation

Docker n'est pas une frontière de sécurité parfaite. Pour du code venant de collègues, c'est
largement suffisant. Pour des pull requests d'inconnus sur un projet public, prévoir une
machine virtuelle dédiée ou gVisor.

## Publication des résultats

Le token GitHub ne doit pas se trouver dans le conteneur qui analyse le code. La publication
des commentaires se fait depuis une étape séparée, avec un token limité aux commentaires de
pull request.
