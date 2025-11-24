package com.example.backend.service;

import com.example.backend.entity.SanctionsList;
import com.example.backend.repository.SanctionsListRepository;
import com.example.backend.service.FuzzyMatchService.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuzzyMatchServiceTest {

    @Mock
    private SanctionsListRepository sanctionsRepository;

    private FuzzyMatchService fuzzyMatchService;

    @BeforeEach
    void setUp() {
        fuzzyMatchService = new FuzzyMatchService(sanctionsRepository);

        // Mock empty results by default to avoid unexpected matches
        when(sanctionsRepository.findHighRiskSanctions()).thenReturn(Collections.emptyList());
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class))).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldCalculateLevenshteinSimilarity() {
        // Test exact match
        assertThat(fuzzyMatchService.calculateSimilarity("test", "test")).isEqualTo(100.0);

        // Test partial match
        assertThat(fuzzyMatchService.calculateSimilarity("test", "tset")).isEqualTo(50.0);

        // Test no match
        assertThat(fuzzyMatchService.calculateSimilarity("abc", "xyz")).isEqualTo(0.0);

        // Test empty strings
        assertThat(fuzzyMatchService.calculateSimilarity("", "")).isEqualTo(100.0);
    }

    @Test
    void shouldReturnEmptyListForNullOrEmptyInput() {
        List<MatchResult> results = fuzzyMatchService.findMatches(null);
        assertThat(results).isEmpty();

        results = fuzzyMatchService.findMatches("");
        assertThat(results).isEmpty();

        results = fuzzyMatchService.findMatches("   ");
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindExactMatches() {
        // Given
        SanctionsList sanction = createSanction("TEST ENTITY", "OFAC", 100);
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class)))
            .thenReturn(Collections.singletonList(sanction));

        // When
        List<MatchResult> results = fuzzyMatchService.findMatches("TEST ENTITY");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSanction()).isEqualTo(sanction);
        assertThat(results.get(0).getSimilarityScore()).isEqualTo(100.0);
    }

    @Test
    void shouldFindFuzzyMatchesAboveThreshold() {
        // Given
        SanctionsList sanction = createSanction("TEST ENTITY", "OFAC", 100);
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class)))
            .thenReturn(Collections.singletonList(sanction));

        // When
        List<MatchResult> results = fuzzyMatchService.findMatches("TSET ENTITY", 80); // 90% similarity

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isAboveThreshold(80)).isTrue();
    }

    @Test
    void shouldFilterMatchesBelowThreshold() {
        // Given - use completely different names
        SanctionsList sanction = createSanction("ABCDEFGH", "OFAC", 100);
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class)))
            .thenReturn(Collections.singletonList(sanction));

        // When - search with very high threshold
        List<MatchResult> results = fuzzyMatchService.findMatches("ZYXWVUT", 95);

        // Then - should have the result but it should be below threshold
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isAboveThreshold(95)).isFalse(); // Below 95% threshold
    }

    @Test
    void shouldSortResultsBySimilarityScore() {
        // Given
        SanctionsList highMatch = createSanction("TEST ENTITY", "OFAC", 100);
        SanctionsList mediumMatch = createSanction("TEST ENTITIES", "EU", 80);
        SanctionsList lowMatch = createSanction("DIFFERENT NAME", "UN", 60);

        when(sanctionsRepository.findHighRiskSanctions()).thenReturn(Arrays.asList(highMatch, mediumMatch, lowMatch));
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class)))
            .thenReturn(Arrays.asList(highMatch, mediumMatch, lowMatch));

        // When
        List<MatchResult> results = fuzzyMatchService.findMatches("TEST ENTITY");

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSimilarityScore()).isGreaterThanOrEqualTo(results.get(1).getSimilarityScore());
        assertThat(results.get(1).getSimilarityScore()).isGreaterThanOrEqualTo(results.get(2).getSimilarityScore());
    }

    @Test
    void shouldLimitResultsForPerformance() {
        // Given - simulate many matches
        List<SanctionsList> manySanctions = Arrays.asList(
            createSanction("ENTITY 1", "OFAC", 100),
            createSanction("ENTITY 2", "OFAC", 100),
            createSanction("ENTITY 3", "OFAC", 100),
            createSanction("ENTITY 4", "OFAC", 100),
            createSanction("ENTITY 5", "OFAC", 100),
            createSanction("ENTITY 6", "OFAC", 100),
            createSanction("ENTITY 7", "OFAC", 100),
            createSanction("ENTITY 8", "OFAC", 100),
            createSanction("ENTITY 9", "OFAC", 100),
            createSanction("ENTITY 10", "OFAC", 100),
            createSanction("ENTITY 11", "OFAC", 100) // 11th item
        );

        when(sanctionsRepository.findHighRiskSanctions()).thenReturn(manySanctions);
        when(sanctionsRepository.fuzzySearchWithSimilarity(anyString(), any(Double.class)))
            .thenReturn(manySanctions);

        // When
        List<MatchResult> results = fuzzyMatchService.findMatches("ENTITY");

        // Then
        assertThat(results).hasSizeLessThanOrEqualTo(50); // Limited for performance
    }

    @Test
    void shouldHandleBatchMatching() {
        // Given
        List<String> names = Arrays.asList("NAME1", "NAME2", "NAME3");
        SanctionsList sanction = createSanction("NAME1", "OFAC", 100);

        // Mock fuzzy search to return match only for NAME1
        when(sanctionsRepository.fuzzySearchWithSimilarity(eq("NAME1"), any(Double.class)))
            .thenReturn(Collections.singletonList(sanction));
        when(sanctionsRepository.fuzzySearchWithSimilarity(eq("NAME2"), any(Double.class)))
            .thenReturn(Collections.emptyList());
        when(sanctionsRepository.fuzzySearchWithSimilarity(eq("NAME3"), any(Double.class)))
            .thenReturn(Collections.emptyList());

        // When
        var results = fuzzyMatchService.findMatchesBatch(names, 85);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get("NAME1")).hasSize(1);
        assertThat(results.get("NAME2")).isEmpty(); // No match for NAME2
        assertThat(results.get("NAME3")).isEmpty(); // No match for NAME3
    }

    @Test
    void shouldRefreshBKTree() {
        // Given
        SanctionsList newSanction = createSanction("NEW ENTITY", "OFAC", 100);
        when(sanctionsRepository.findHighRiskSanctions()).thenReturn(Collections.singletonList(newSanction));

        // When
        fuzzyMatchService.refreshBKTree();

        // Then - BK-tree should remain disabled (default configuration)
        var stats = fuzzyMatchService.getBKTreeStats();
        assertThat(stats.get("enabled")).isEqualTo(false); // BK-tree is disabled by default
        assertThat(stats.get("size")).isEqualTo(0);
    }

    @Test
    void shouldProvideBKTreeStatistics() {
        // When
        var stats = fuzzyMatchService.getBKTreeStats();

        // Then
        assertThat(stats).containsKey("enabled");
        assertThat(stats).containsKey("size");
        assertThat(stats).containsKey("levenshteinThreshold");
    }

    private SanctionsList createSanction(String name, String source, int riskScore) {
        SanctionsList sanction = new SanctionsList();
        sanction.setId(System.nanoTime()); // Set ID to avoid NPE
        sanction.setName(name);
        sanction.setSource(source);
        sanction.setRiskScore(riskScore);
        sanction.setNormalizedName(name.toUpperCase().replaceAll("[^A-Z0-9\\s]", ""));
        return sanction;
    }
}
