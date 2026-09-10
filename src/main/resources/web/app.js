"use strict";

/*
 * Interface de l'AI Project Reviewer.
 *
 * Trois règles suivies dans tout ce fichier :
 *
 *  1. Aucun texte venant du serveur n'est inséré via innerHTML. Ces textes proviennent du
 *     modèle, donc indirectement du projet évalué, qui n'est pas fiable. On passe toujours
 *     par textContent, qui n'interprète rien.
 *  2. Aucun script en ligne : l'en-tête Content-Security-Policy du serveur les interdit.
 *  3. L'évaluation dure plusieurs minutes, donc on lance puis on interroge périodiquement.
 */

const POLL_INTERVAL_MS = 2000;

const el = (id) => document.getElementById(id);

let pollTimer = null;

document.addEventListener("DOMContentLoaded", () => {
    el("load").addEventListener("click", loadProject);
    el("start").addEventListener("click", startEvaluation);
    loadCriteria();
});

/** Affiche un message d'erreur, ou l'efface si le message est vide. */
function showError(message) {
    const box = el("error");
    box.textContent = message || "";
    box.hidden = !message;
}

/** Charge la liste des critères et construit les cases à cocher. */
async function loadCriteria() {
    try {
        const response = await fetch("/api/criteria");
        const data = await response.json();
        const container = el("criteria");
        container.replaceChildren();

        for (const criterion of data.criteria) {
            const label = document.createElement("label");
            label.className = "checkbox";

            const box = document.createElement("input");
            box.type = "checkbox";
            box.value = criterion.id;
            box.checked = true;
            label.appendChild(box);

            const text = document.createElement("span");
            text.textContent = `${criterion.label} (/${criterion.maxScore})`;
            label.appendChild(text);

            const badge = document.createElement("em");
            badge.textContent = criterion.usesLlm ? " modèle" : " déterministe";
            label.appendChild(badge);

            container.appendChild(label);
        }
    } catch (e) {
        showError("Impossible de charger la liste des critères.");
    }
}

/** Charge le projet et affiche son arborescence. */
async function loadProject() {
    const project = el("project").value.trim();
    if (!project) {
        showError("Indiquez d'abord un projet.");
        return;
    }
    showError("");

    try {
        const response = await fetch("/api/project", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({project})
        });
        const data = await response.json();
        if (!response.ok) {
            showError(data.error || "Projet illisible.");
            return;
        }
        renderTree(data);
        el("criteria-panel").hidden = false;
    } catch (e) {
        showError("Le serveur n'a pas répondu.");
    }
}

/** Affiche l'inventaire et l'arborescence du projet chargé. */
function renderTree(data) {
    el("tree-summary").textContent =
        `${data.name} · ${data.origin}${data.revision ? " · " + data.revision : ""} · ` +
        `${data.fileCount} fichier(s), ${data.javaLines} lignes de Java` +
        (data.truncated ? " (liste tronquée)" : "");

    const tree = el("tree");
    tree.replaceChildren();
    for (const file of data.files) {
        const row = document.createElement("div");
        row.className = "tree-row";

        const path = document.createElement("code");
        path.textContent = file.path;
        row.appendChild(path);

        const kind = document.createElement("span");
        kind.className = "kind";
        kind.textContent = file.kind;
        row.appendChild(kind);

        tree.appendChild(row);
    }
    el("tree-panel").hidden = false;
}

/** Lance une évaluation et commence à interroger l'état. */
async function startEvaluation() {
    const project = el("project").value.trim();
    const criteria = Array.from(document.querySelectorAll("#criteria input:checked"))
        .map((box) => box.value);
    const exclude = el("exclude").value.split("\n").map((s) => s.trim()).filter(Boolean);

    if (criteria.length === 0) {
        showError("Sélectionnez au moins un critère.");
        return;
    }
    showError("");

    try {
        const response = await fetch("/api/evaluate", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                project,
                criteria,
                exclude,
                model: el("model").value.trim(),
                offline: el("offline").checked
            })
        });
        if (!response.ok) {
            const data = await response.json();
            showError(data.error || "Évaluation refusée.");
            return;
        }
        el("progress-panel").hidden = false;
        el("results-panel").hidden = true;
        poll();
    } catch (e) {
        showError("Le serveur n'a pas répondu.");
    }
}

/** Interroge l'état courant, et se replanifie tant que l'évaluation tourne. */
async function poll() {
    clearTimeout(pollTimer);
    try {
        const response = await fetch("/api/status");
        const state = await response.json();
        renderProgress(state);

        if (state.status === "RUNNING") {
            pollTimer = setTimeout(poll, POLL_INTERVAL_MS);
        } else if (state.status === "DONE") {
            renderResults(state.result);
        } else if (state.status === "FAILED") {
            showError(state.error || "L'évaluation a échoué.");
        }
    } catch (e) {
        showError("Perte de contact avec le serveur.");
    }
}

/** Met à jour la barre de progression et la liste des avertissements. */
function renderProgress(state) {
    el("stage").textContent = state.stage;
    el("bar").max = Math.max(1, state.total);
    el("bar").value = state.current;
    el("counter").textContent = state.total
        ? `${state.current}/${state.total} — ${state.currentLabel}`
        : "";

    const warnings = el("warnings");
    warnings.replaceChildren();
    for (const message of state.warnings) {
        const line = document.createElement("p");
        line.className = "warning";
        line.textContent = message;
        warnings.appendChild(line);
    }
}

/** Affiche le tableau des notes et le détail par critère. */
function renderResults(result) {
    el("overall").textContent =
        `${result.project} · ${result.overallScore.toFixed(1)}/20 · ` +
        `${result.llmCalls} appel(s) au modèle (${result.model}) en ${result.durationSeconds} s` +
        (result.partial ? " · certains critères n'ont pas pu être évalués" : "");

    const body = el("scores").querySelector("tbody");
    body.replaceChildren();
    for (const criterion of result.criteria) {
        const row = document.createElement("tr");
        row.appendChild(cell(criterion.label));
        row.appendChild(cell(criterion.evaluated ? String(criterion.score) : "n/a"));
        row.appendChild(cell(String(criterion.maxScore)));
        body.appendChild(row);
    }

    const details = el("details");
    details.replaceChildren();
    for (const criterion of result.criteria) {
        details.appendChild(renderCriterion(criterion));
    }
    el("results-panel").hidden = false;
}

/** Le détail d'un critère : appréciation, forces, faiblesses, recommandations, signalements. */
function renderCriterion(criterion) {
    const section = document.createElement("section");
    section.className = "criterion";

    const title = document.createElement("h3");
    title.textContent = criterion.evaluated
        ? `${criterion.label} — ${criterion.score}/${criterion.maxScore}`
        : `${criterion.label} — non évalué`;
    section.appendChild(title);

    const summary = document.createElement("p");
    summary.textContent = criterion.summary;
    section.appendChild(summary);

    appendList(section, "Points forts", criterion.strengths);
    appendList(section, "Points faibles", criterion.weaknesses);
    appendList(section, "Recommandations", criterion.recommendations);

    if (criterion.findings.length > 0) {
        const heading = document.createElement("h4");
        heading.textContent = "Signalements";
        section.appendChild(heading);

        const list = document.createElement("ul");
        for (const finding of criterion.findings) {
            const item = document.createElement("li");

            const severity = document.createElement("strong");
            severity.textContent = finding.severity;
            item.appendChild(severity);

            // textContent, jamais innerHTML : ce texte vient du modèle.
            const text = document.createElement("span");
            text.textContent = ` ${finding.where} — ${finding.title} : ${finding.explanation}`;
            item.appendChild(text);

            list.appendChild(item);
        }
        section.appendChild(list);
    }
    return section;
}

function appendList(parent, title, items) {
    if (!items || items.length === 0) {
        return;
    }
    const heading = document.createElement("h4");
    heading.textContent = title;
    parent.appendChild(heading);

    const list = document.createElement("ul");
    for (const item of items) {
        const li = document.createElement("li");
        li.textContent = item;
        list.appendChild(li);
    }
    parent.appendChild(list);
}

function cell(text) {
    const td = document.createElement("td");
    td.textContent = text;
    return td;
}
