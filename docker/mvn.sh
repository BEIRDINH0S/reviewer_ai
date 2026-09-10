#!/usr/bin/env bash
#
# Lance Maven dans un conteneur : rien à installer sur la machine, ni JDK ni Maven.
#
#   docker/mvn.sh test           lance les tests
#   docker/mvn.sh package        construit le jar
#   docker/mvn.sh javadoc:javadoc  génère la documentation
#
# Le cache Maven est conservé dans un volume Docker, donc seul le premier appel télécharge
# les dépendances.
#
set -euo pipefail

# Sous Git Bash (Windows), MSYS réécrit les chemins qui commencent par « / » avant de les
# passer au programme : /app deviendrait C:/Program Files/Git/app. On le désactive.
export MSYS_NO_PATHCONV=1

IMAGE=maven:3.9-eclipse-temurin-25
CACHE=ai-reviewer-m2

# Docker Desktop pour Windows attend un chemin de la forme C:/Users/..., alors que Git Bash
# donne /c/Users/... ; cygpath fait la conversion et n'existe que là où elle est nécessaire.
host_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    echo "$1"
  fi
}

# -it seulement si on est dans un terminal, sinon Docker refuse de démarrer
# (appel depuis un script ou depuis l'intégration continue).
# Volontairement sans guillemets plus bas : la variable vide doit disparaître.
TTY_FLAG=""
[ -t 0 ] && TTY_FLAG="-it"

# shellcheck disable=SC2086
docker run --rm $TTY_FLAG \
  -v "$(host_path "$(git rev-parse --show-toplevel)")":/app -w /app \
  -v "$CACHE":/root/.m2 \
  "$IMAGE" \
  mvn --batch-mode "$@"
