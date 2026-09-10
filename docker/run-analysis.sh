#!/usr/bin/env bash
#
# Évalue un projet dans un conteneur jetable.
#
#   docker/run-analysis.sh /chemin/du/projet [critères séparés par des virgules]
#
# Le projet est monté en LECTURE SEULE : le conteneur ne peut pas le modifier, et aucun
# système de construction n'est présent dans l'image, donc il ne peut pas l'exécuter.
#
# Deux modes, selon d'où vient le code analysé :
#
#   MODE=isolated (défaut) — aucun accès réseau. Ollama doit tourner dans un conteneur
#                            attaché au même réseau interne (voir docker/README.md).
#                            À utiliser pour tout projet qui ne vient pas de l'équipe.
#
#   MODE=trusted           — le conteneur peut joindre l'Ollama installé sur la machine.
#                            Plus rapide, mais l'isolation réseau est levée.
#                            À réserver au projet de test de l'équipe.
#
set -euo pipefail

# Sous Git Bash (Windows), MSYS réécrit les chemins qui commencent par « / » avant de les
# passer au programme : /repo deviendrait C:/Program Files/Git/repo. On le désactive.
export MSYS_NO_PATHCONV=1

# Docker Desktop pour Windows attend un chemin de la forme C:/Users/..., alors que Git Bash
# donne /c/Users/... ; cygpath fait la conversion et n'existe que là où elle est nécessaire.
host_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    echo "$1"
  fi
}

PROJECT="${1:?chemin du projet à évaluer}"
CRITERIA="${2:-}"

MODE="${MODE:-isolated}"
NETWORK=reviewer-net
IMAGE=ai-reviewer

docker build -f docker/Dockerfile -t "$IMAGE" .

case "$MODE" in
  isolated)
    # Réseau sans route vers l'extérieur : le conteneur ne parle qu'aux autres conteneurs
    # attachés à ce même réseau.
    docker network inspect "$NETWORK" >/dev/null 2>&1 \
      || docker network create --internal "$NETWORK"
    NET_ARGS="--network $NETWORK"
    OLLAMA_URL="http://ollama:11434"
    ;;
  trusted)
    # host.docker.internal désigne la machine hôte depuis un conteneur Docker Desktop.
    NET_ARGS=""
    OLLAMA_URL="http://host.docker.internal:11434"
    ;;
  *)
    echo "MODE doit valoir isolated ou trusted" >&2
    exit 1
    ;;
esac

mkdir -p out

# NET_ARGS volontairement sans guillemets : vide, elle doit disparaître.
# shellcheck disable=SC2086
docker run --rm $NET_ARGS \
  --read-only --tmpfs /tmp \
  --user 1000:1000 \
  --cap-drop ALL --security-opt no-new-privileges \
  --memory 4g --cpus 2 --pids-limit 256 \
  -v "$(host_path "$(cd "$PROJECT" && pwd)")":/projet:ro \
  -v "$(host_path "$(pwd)")/out":/out \
  "$IMAGE" \
  --project /projet \
  ${CRITERIA:+--criteria "$CRITERIA"} \
  --out /out/evaluation.tex --ollama-url "$OLLAMA_URL"
