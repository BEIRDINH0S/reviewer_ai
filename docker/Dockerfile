# Image d'analyse : ne contient que le jar et un JDK.
#
# Le dépôt analysé n'est jamais copié dans l'image, il est monté en lecture seule au lancement.
# Aucun outil de build n'est présent : impossible d'exécuter le pom.xml du dépôt analysé, même
# par erreur.

# Étape de construction : Maven n'existe que dans cette étape, pas dans l'image finale.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Les dépendances sont résolues avant de copier les sources : tant que le pom.xml ne change
# pas, cette couche est réutilisée et la construction est rapide.
COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode package -DskipTests

# Image finale : JRE seul.
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/ai-reviewer.jar ./ai-reviewer.jar

# Utilisateur sans privilèges. Le conteneur est aussi lancé avec --read-only.
# L'image de base Temurin livre déjà un utilisateur d'uid 1000 : on ne le recrée que s'il
# manque, sinon useradd échoue (« UID already in use »). Ce qui compte est de tourner en 1000.
RUN useradd --uid 1000 --create-home reviewer 2>/dev/null || true
USER 1000:1000

# -Xmx explicite : un fichier piégé ne doit pas pouvoir saturer la machine hôte.
ENTRYPOINT ["java", "-Xmx3g", "-jar", "/app/ai-reviewer.jar"]
