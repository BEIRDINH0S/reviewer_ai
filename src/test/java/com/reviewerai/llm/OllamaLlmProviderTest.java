package com.reviewerai.llm;

import com.reviewerai.config.EvaluationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link OllamaLlmProvider}.
 *
 * <p>Aucun modèle, aucun réseau : on vérifie la construction hors ligne et la traduction des
 * échecs. Volontairement, ce test n'importe aucun type de LangChain4j — les branches qui en
 * dépendent (modèle inconnu, erreur HTTP) sont couvertes par les vérifications sur un vrai
 * Ollama, décrites dans l'issue. Ici on exerce les signaux réseau du JDK, dont le cas décisif :
 * un serveur arrêté doit être jugé passager pour que le réessai ait lieu.
 */
class OllamaLlmProviderTest {

    private static final String MODEL = "qwen2.5-coder:7b";

    @Test
    @DisplayName("construire le fournisseur n'ouvre aucune connexion")
    void constructionIsOffline() {
        EvaluationConfig config = EvaluationConfig.builder()
                .projectSource(Path.of("."))
                .modelName(MODEL)
                .ollamaBaseUrl("http://127.0.0.1:11434")
                .build();

        OllamaLlmProvider provider = new OllamaLlmProvider(config);

        assertEquals(MODEL, provider.modelName());
    }

    @Test
    @DisplayName("un serveur arrêté (connexion refusée) est un échec passager")
    void connectionRefusedIsRetryable() {
        LlmException translated = OllamaLlmProvider.classify(new ConnectException("Connection refused"), MODEL);

        assertTrue(translated.isRetryable(), "un serveur arrêté doit pouvoir être réessayé");
    }

    @Test
    @DisplayName("un délai dépassé est un échec passager")
    void timeoutIsRetryable() {
        LlmException translated = OllamaLlmProvider.classify(new SocketTimeoutException("read timed out"), MODEL);

        assertTrue(translated.isRetryable());
    }

    @Test
    @DisplayName("un hôte introuvable est un échec définitif")
    void unknownHostIsPermanent() {
        LlmException translated = OllamaLlmProvider.classify(new UnknownHostException("nowhere"), MODEL);

        assertFalse(translated.isRetryable(), "une adresse de serveur fausse ne s'arrangera pas en réessayant");
    }

    @Test
    @DisplayName("un signal réseau enfoui dans la chaîne des causes est quand même reconnu")
    void inspectsCauseChain() {
        RuntimeException wrapped = new RuntimeException("appel au modèle échoué",
                new IllegalStateException(new ConnectException("Connection refused")));

        LlmException translated = OllamaLlmProvider.classify(wrapped, MODEL);

        assertTrue(translated.isRetryable(), "la connexion refusée est enfouie mais décisive");
    }

    @Test
    @DisplayName("un échec non reconnu n'est pas réessayé, et sa cause est conservée")
    void unknownFailureIsPermanentAndKeepsCause() {
        RuntimeException boom = new RuntimeException("panne inattendue");

        LlmException translated = OllamaLlmProvider.classify(boom, MODEL);

        assertFalse(translated.isRetryable());
        assertSame(boom, translated.getCause());
    }
}
