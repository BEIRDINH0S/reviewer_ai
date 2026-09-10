package com.reviewerai.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de {@link MethodRef}.
 *
 * <p>Ces tests protègent le contrat le plus important du projet : deux références vers la même
 * méthode, construites par des briques différentes, doivent être égales.
 */
class MethodRefTest {

    @Test
    @DisplayName("deux références à la même méthode sont égales, même à des lignes différentes")
    void equalsIgnoresLineNumbers() {
        MethodRef fromDiff = MethodRef.of("src/A.java", "com.example.A", "run", List.of("int"), 10, 20);
        MethodRef fromGraph = MethodRef.of("src/A.java", "com.example.A", "run", List.of("int"), 42, 55);

        assertEquals(fromDiff, fromGraph);
        assertEquals(fromDiff.hashCode(), fromGraph.hashCode());
    }

    @Test
    @DisplayName("une surcharge est une méthode différente")
    void overloadsAreDistinct() {
        MethodRef intVersion = MethodRef.of("src/A.java", "com.example.A", "run", List.of("int"), 1, 2);
        MethodRef stringVersion = MethodRef.of("src/A.java", "com.example.A", "run", List.of("java.lang.String"), 1, 2);

        assertNotEquals(intVersion, stringVersion);
    }

    @Test
    @DisplayName("displayName garde la classe simple et le nom de la méthode")
    void displayNameIsReadable() {
        MethodRef ref = MethodRef.of("src/A.java", "com.example.OrderService", "applyDiscount",
                List.of("java.lang.String", "int"), 1, 2);

        assertEquals("OrderService#applyDiscount", ref.displayName());
    }

    @Test
    @DisplayName("la signature ne dépend pas du nom des paramètres")
    void signatureFormat() {
        assertEquals("com.example.A#run(int,java.lang.String)",
                MethodRef.signatureOf("com.example.A", "run", List.of("int", "java.lang.String")));
    }
}
