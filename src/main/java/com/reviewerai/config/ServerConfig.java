package com.reviewerai.config;

/**
 * Réglages du serveur web.
 *
 * <p>Séparé de {@link ReviewerConfig} parce que ces deux objets n'ont ni le même cycle de vie
 * ni la même origine : le serveur est configuré une fois au démarrage, tandis que les
 * paramètres d'une analyse arrivent du formulaire, à chaque requête.
 *
 * @param bindAddress interface d'écoute
 * @param port        port d'écoute
 */
public record ServerConfig(String bindAddress, int port) {

    /** Écoute locale uniquement : personne d'autre sur le réseau ne peut lancer d'analyse. */
    public static final String LOOPBACK = "127.0.0.1";

    /**
     * Toutes les interfaces. Nécessaire dans un conteneur, où l'écoute sur la boucle locale
     * serait injoignable depuis la machine hôte. La protection vient alors de la publication
     * de port côté hôte : {@code -p 127.0.0.1:8080:8080}.
     */
    public static final String ALL_INTERFACES = "0.0.0.0";

    /**
     * Demande au système de choisir un port libre. Utilisé par les tests, pour que deux
     * exécutions simultanées n'entrent pas en conflit.
     */
    public static final int ANY_FREE_PORT = 0;

    public ServerConfig {
        if (port < ANY_FREE_PORT || port > 65535) {
            throw new IllegalArgumentException("port hors bornes : " + port);
        }
    }

    /** Réglage par défaut : écoute locale sur le port 8080. */
    public static ServerConfig localhost() {
        return new ServerConfig(LOOPBACK, 8080);
    }

    /** L'adresse à ouvrir dans le navigateur. */
    public String url() {
        String host = ALL_INTERFACES.equals(bindAddress) ? "localhost" : bindAddress;
        return "http://" + host + ":" + port;
    }
}
