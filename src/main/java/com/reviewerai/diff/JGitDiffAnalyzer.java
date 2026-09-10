package com.reviewerai.diff;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.ChangedMethod;
import com.reviewerai.model.MethodRef;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Trouve les méthodes ajoutées ou modifiées entre deux commits, avec JGit et JavaParser.
 *
 * <p>Fonctionnalité optionnelle du sujet — comparaison de deux versions d'un projet — bâtie sur
 * la révision précise que sait charger {@code GitProjectLoader}. Le {@code DiffFormatter} de JGit
 * donne les fichiers et les plages de lignes modifiées ; JavaParser retrouve quelle méthode
 * contient chaque plage.
 *
 * <p><b>Sécurité</b> : lecture seule du dépôt. Aucune commande git externe, aucun build, pas
 * d'initialisation de sous-modules. JGit lit les objets lui-même, sans passer par un shell.
 *
 * <p>Choix assumés : seule la version HEAD est parsée — une méthode supprimée n'a plus de code à
 * évaluer, donc n'apparaît pas. La détection de renommage est activée, ce qui évite qu'un fichier
 * déplacé ressorte comme entièrement réécrit. Un fichier qui ne parse pas est ignoré sans faire
 * échouer l'analyse.
 */
public final class JGitDiffAnalyzer implements DiffAnalyzer {

    private final EvaluationConfig config;

    public JGitDiffAnalyzer(EvaluationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public List<ChangedMethod> findChangedMethods(Path repo, String baseCommit, String headCommit) {
        try (Repository repository = openRepository(repo)) {
            ObjectId baseId = resolve(repository, baseCommit);
            ObjectId headId = resolve(repository, headCommit);
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit base = walk.parseCommit(baseId);
                RevCommit head = walk.parseCommit(headId);
                return analyse(repository, base, head);
            }
        } catch (IOException e) {
            throw new DiffException("Dépôt git illisible : " + repo, e);
        }
    }

    private List<ChangedMethod> analyse(Repository repository, RevCommit base, RevCommit head)
            throws IOException {
        List<ChangedMethod> changed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            for (DiffEntry entry : formatter.scan(base.getTree(), head.getTree())) {
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue; // méthode disparue : plus rien à évaluer
                }
                String path = entry.getNewPath();
                if (!path.endsWith(".java")) {
                    continue; // le diff peut porter sur n'importe quel fichier ; on ne lit que du Java
                }
                try {
                    collect(repository, head, entry, formatter, path, changed, seen);
                } catch (RuntimeException | StackOverflowError e) {
                    // Un fichier qui ne parse pas ne doit pas emporter l'analyse entière.
                }
            }
        }
        return List.copyOf(changed);
    }

    private void collect(Repository repository, RevCommit head, DiffEntry entry, DiffFormatter formatter,
                         String path, List<ChangedMethod> changed, Set<String> seen) throws IOException {
        String content = readBlob(repository, head, path);
        if (content == null) {
            return; // fichier absent de HEAD ou trop gros
        }
        Optional<CompilationUnit> parsed = new JavaParser().parse(content).getResult();
        if (parsed.isEmpty()) {
            return; // Java invalide : ignoré
        }

        boolean wholeFileAdded = entry.getChangeType() == DiffEntry.ChangeType.ADD;
        EditList edits = formatter.toFileHeader(entry).toEditList();

        for (MethodDeclaration method : parsed.get().findAll(MethodDeclaration.class)) {
            int start = method.getBegin().map(position -> position.line).orElse(0);
            int end = method.getEnd().map(position -> position.line).orElse(start);
            if (start == 0) {
                continue;
            }
            List<Integer> touched = wholeFileAdded
                    ? linesBetween(start, end)
                    : changedLinesWithin(edits, start, end);
            if (touched.isEmpty()) {
                continue;
            }
            MethodRef ref = MethodRef.of(path, className(method), method.getNameAsString(),
                    parameterTypes(method), start, end);
            if (seen.add(ref.signature() + '@' + path)) {
                ChangedMethod.ChangeType type = wholeFileAdded
                        ? ChangedMethod.ChangeType.ADDED
                        : ChangedMethod.ChangeType.MODIFIED;
                changed.add(new ChangedMethod(ref, type, touched, method.toString()));
            }
        }
    }

    /** Les lignes HEAD, à l'intérieur de {@code [start,end]}, que le diff a touchées. */
    private static List<Integer> changedLinesWithin(EditList edits, int start, int end) {
        List<Integer> lines = new ArrayList<>();
        for (Edit edit : edits) {
            // Côté B (HEAD) : indices 0-based, borne haute exclue → lignes 1-based beginB+1..endB.
            int from = Math.max(edit.getBeginB() + 1, start);
            int to = Math.min(edit.getEndB(), end);
            for (int line = from; line <= to; line++) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<Integer> linesBetween(int start, int end) {
        List<Integer> lines = new ArrayList<>();
        for (int line = start; line <= end; line++) {
            lines.add(line);
        }
        return lines;
    }

    private String readBlob(Repository repository, RevCommit commit, String path) throws IOException {
        try (TreeWalk walk = TreeWalk.forPath(repository, path, commit.getTree())) {
            if (walk == null) {
                return null;
            }
            ObjectLoader loader = repository.open(walk.getObjectId(0), Constants.OBJ_BLOB);
            if (loader.getSize() > config.maxFileSizeBytes()) {
                return null;
            }
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private ObjectId resolve(Repository repository, String revision) throws IOException {
        ObjectId id = repository.resolve(revision);
        if (id == null) {
            throw new DiffException("Révision introuvable dans le dépôt : " + revision, null);
        }
        return id;
    }

    private static String className(MethodDeclaration method) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .orElseGet(() -> method.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(""));
    }

    private static List<String> parameterTypes(MethodDeclaration method) {
        return method.getParameters().stream().map(parameter -> parameter.getType().asString()).toList();
    }

    private static Repository openRepository(Path repo) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(repo.toAbsolutePath().normalize().resolve(".git").toFile())
                .readEnvironment()
                .build();
    }
}
