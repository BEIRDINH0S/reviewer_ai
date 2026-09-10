package com.reviewerai.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Accès disque défensif au projet évalué.
 *
 * <p>Le code évalué est considéré comme non fiable : on le lit, on ne l'exécute jamais. Cette
 * classe centralise les garde-fous pour qu'aucune brique n'ait à les réimplémenter, et pour
 * qu'un oubli dans une brique ne devienne pas une faille.
 *
 * <p>Ce qui est filtré ici :
 * <ul>
 *   <li>les liens symboliques, qui pourraient pointer hors du projet (ex. {@code /etc/passwd}) ;
 *   <li>les chemins qui s'échappent de la racine après normalisation ;
 *   <li>les fichiers trop gros, qui feraient exploser la mémoire du parser ;
 *   <li>les répertoires de construction et de dépendances, sans intérêt pour l'évaluation ;
 *   <li>à l'extraction d'archive, les entrées dont le chemin sort du répertoire cible
 *       (<i>zip slip</i>) et les archives dont le contenu décompressé est démesuré
 *       (<i>zip bomb</i>).
 * </ul>
 */
public final class SafeFiles {

    /** Répertoires ignorés partout : ils ne contiennent rien qu'on veuille évaluer. */
    private static final List<String> IGNORED_DIRS =
            List.of(".git", "target", "build", "out", "node_modules", ".gradle", ".mvn", ".idea", "dist");

    /** Plafond global à l'extraction d'une archive : au-delà, on refuse de continuer. */
    public static final long MAX_ARCHIVE_TOTAL_BYTES = 512L * 1024 * 1024;

    /** Plafond du nombre d'entrées d'une archive. */
    public static final int MAX_ARCHIVE_ENTRIES = 20_000;

    private SafeFiles() {
    }

    /**
     * Liste tous les fichiers analysables sous {@code root}.
     *
     * <p>Contrairement à l'ancienne version, ne se limite pas au {@code .java} : le sujet
     * demande d'identifier les descripteurs de construction, les fichiers Docker, la
     * documentation et les scripts, qui sont autant de matière à évaluer.
     *
     * @param root         racine du projet
     * @param maxSizeBytes taille maximale acceptée par fichier
     * @return les chemins retenus, triés pour que deux exécutions donnent le même ordre
     */
    public static List<Path> listFiles(Path root, long maxSizeBytes) {
        Path base = root.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(base, FileVisitOptionsNoFollow.INSTANCE)) {
            List<Path> result = new ArrayList<>();
            walk.filter(p -> !isIgnored(base, p))
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> isWithin(base, p))
                    .filter(p -> isSmallEnough(p, maxSizeBytes))
                    .forEach(result::add);
            result.sort(Path::compareTo);
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de parcourir " + root, e);
        }
    }

    /** Les seuls fichiers {@code .java}, raccourci utilisé par la brique Graphe. */
    public static List<Path> javaSources(Path root, long maxSizeBytes) {
        return listFiles(root, maxSizeBytes).stream()
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
    }

    /**
     * Lit un fichier texte du projet.
     *
     * @return le contenu, ou {@link Optional#empty()} si le fichier est illisible, trop gros,
     *         hors du projet, ou n'est pas du texte UTF-8 valide
     */
    public static Optional<String> readText(Path root, Path file, long maxSizeBytes) {
        Path base = root.toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (!isWithin(base, target) || !isSmallEnough(target, maxSizeBytes)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (MalformedInputException e) {
            return Optional.empty(); // fichier binaire déguisé en source
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Extrait une archive ZIP dans un répertoire cible.
     *
     * <p>Deux attaques classiques sont bloquées ici, et c'est la raison d'être de la méthode :
     * <ul>
     *   <li><b>zip slip</b> — une entrée nommée {@code ../../.ssh/authorized_keys} écrirait
     *       hors du répertoire cible. Chaque destination est donc vérifiée après normalisation ;
     *   <li><b>zip bomb</b> — quelques kilo-octets compressés peuvent donner des gigaoctets.
     *       On plafonne le volume décompressé et le nombre d'entrées.
     * </ul>
     *
     * @param archive     l'archive à extraire
     * @param target      répertoire de destination, créé s'il n'existe pas
     * @param maxTotal    volume décompressé maximal accepté
     * @throws IOException si l'archive est illisible ou dépasse les plafonds
     */
    public static void extractZip(Path archive, Path target, long maxTotal) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        Files.createDirectories(base);

        long written = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Archive refusée : plus de " + MAX_ARCHIVE_ENTRIES + " entrées");
                }
                Path destination = base.resolve(entry.getName()).normalize();
                if (!destination.startsWith(base)) {
                    // Chemin qui remonte hors de la cible : archive piégée, on arrête tout.
                    throw new IOException("Archive refusée : entrée hors du répertoire cible : " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                written += copyLimited(zip, destination, maxTotal - written);
                if (written > maxTotal) {
                    throw new IOException("Archive refusée : contenu décompressé supérieur à " + maxTotal + " octets");
                }
            }
        }
    }

    /**
     * Chemin d'un fichier relatif à la racine du projet, toujours avec des {@code /}.
     *
     * <p>À utiliser systématiquement. Sous Windows, {@link Path} sépare avec des {@code \}
     * alors que JGit renvoie des {@code /} : sans normalisation, deux briques produiraient
     * deux chemins différents pour le même fichier.
     */
    public static String toRepoRelative(Path root, Path file) {
        Path relative = root.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize());
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }

    /** Compte les lignes d'un fichier texte ; {@code 0} s'il est illisible ou binaire. */
    public static int countLines(Path root, Path file, long maxSizeBytes) {
        return readText(root, file, maxSizeBytes)
                .map(text -> text.isEmpty() ? 0 : (int) text.lines().count())
                .orElse(0);
    }

    /** Vrai si {@code candidate} est bien sous {@code base} une fois les {@code ..} résolus. */
    public static boolean isWithin(Path base, Path candidate) {
        return candidate.toAbsolutePath().normalize().startsWith(base.toAbsolutePath().normalize());
    }

    /** Taille d'un fichier, {@code -1} si elle n'est pas lisible. */
    public static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private static long copyLimited(InputStream in, Path destination, long remaining) throws IOException {
        if (remaining <= 0) {
            throw new IOException("Archive refusée : contenu décompressé trop volumineux");
        }
        byte[] buffer = new byte[8192];
        long total = 0;
        try (var out = Files.newOutputStream(destination)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > remaining) {
                    throw new IOException("Archive refusée : contenu décompressé trop volumineux");
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static boolean isSmallEnough(Path p, long maxSizeBytes) {
        long size = sizeOf(p);
        return size >= 0 && size <= maxSizeBytes;
    }

    private static boolean isIgnored(Path base, Path candidate) {
        Path relative = base.relativize(candidate);
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link Files#walk} suit les liens symboliques dès qu'on lui passe {@code FOLLOW_LINKS}.
     * On ne lui passe donc rien : c'est le comportement voulu, mais l'intention mérite d'être
     * nommée plutôt que laissée à un tableau vide énigmatique.
     */
    private static final class FileVisitOptionsNoFollow {
        static final java.nio.file.FileVisitOption[] INSTANCE = new java.nio.file.FileVisitOption[0];
    }
}
