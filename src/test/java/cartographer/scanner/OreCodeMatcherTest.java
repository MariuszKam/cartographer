package cartographer.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreCodeMatcherTest {

    @Test
    void matchesOreWithoutQuality() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "ore-nativecopper-granite", "nativecopper"));
    }

    @Test
    void matchesPoorOre() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "ore-poor-nativecopper-granite", "nativecopper"));
    }

    @Test
    void matchesMediumOre() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "ore-medium-cassiterite-granite", "cassiterite"));
    }

    @Test
    void matchesRichOre() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "ore-rich-cassiterite-granite", "cassiterite"));
    }

    @Test
    void matchesNamespacedOre() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "game:ore-nativecopper-granite", "nativecopper"));
    }

    @Test
    void matchesModdedNamespacedOre() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "mod:ore-deep-silver-basalt", "deep-silver"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "GAME:ORE-RICH-CASSITERITE-GRANITE", "CASSITERITE"));
    }

    @Test
    void trimsMatch() {
        assertTrue(OreCodeMatcher.matchesOreCode(
                "ore-nativecopper-granite", " nativecopper "));
    }

    @Test
    void rejectsForestBlock() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "forest-nativecopper", "nativecopper"));
    }

    @Test
    void rejectsNamespacedForestBlock() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "game:forest-nativecopper", "nativecopper"));
    }

    @Test
    void rejectsRockBlock() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "rock-cassiterite-granite", "cassiterite"));
    }

    @Test
    void rejectsDecorativeOreSubstring() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "decorative-ore-cassiterite", "cassiterite"));
    }

    @Test
    void rejectsSomethingOreSubstring() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "somethingore-nativecopper", "nativecopper"));
    }

    @Test
    void rejectsWrongResource() {
        assertFalse(OreCodeMatcher.matchesOreCode(
                "ore-poor-cassiterite-granite", "nativecopper"));
    }

    @Test
    void rejectsNullCode() {
        assertFalse(OreCodeMatcher.matchesOreCode(null, "copper"));
    }

    @Test
    void rejectsNullMatch() {
        assertFalse(OreCodeMatcher.matchesOreCode("ore-copper-granite", null));
    }

    @Test
    void rejectsBlankMatch() {
        assertFalse(OreCodeMatcher.matchesOreCode("ore-copper-granite", "   "));
    }
}
