package com.example.backend.service;

import com.example.backend.entity.SanctionsList;
import com.example.backend.repository.SanctionsListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * Service providing fuzzy string matching capabilities for sanctions screening.
 * Implements Levenshtein distance calculation and BK-tree algorithms for efficient similarity matching.
 *
 * FR-04: Sanctions lookup using fuzzy matching
 * FR-05: Similarity score threshold (default 85%)
 */
@Service
public class FuzzyMatchService {

    private static final Logger logger = LoggerFactory.getLogger(FuzzyMatchService.class);

    private final SanctionsListRepository sanctionsRepository;
    private final ForkJoinPool forkJoinPool;

    @Value("${compliance.fuzzy.levenshtein-threshold:85}")
    private int levenshteinThreshold;

    @Value("${compliance.fuzzy.bk-tree-enabled:true}")
    private boolean bkTreeEnabled;

    @Value("${compliance.fuzzy.cache-size:10000}")
    private int cacheSize;

    @Value("${compliance.fuzzy.batch-size:100}")
    private int batchSize;

    // In-memory BK-tree for high-performance matching
    private BKTree bkTree;

    @Autowired
    public FuzzyMatchService(SanctionsListRepository sanctionsRepository) {
        this.sanctionsRepository = sanctionsRepository;
        this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        this.bkTree = new BKTree();

        // Initialize BK-tree with high-risk sanctions
        initializeBKTree();
    }

    /**
     * Initialize BK-tree with high-risk sanctions for fast matching.
     */
    private void initializeBKTree() {
        if (!bkTreeEnabled) {
            logger.info("BK-tree disabled, skipping initialization");
            return;
        }

        logger.info("Initializing BK-tree with high-risk sanctions");
        List<SanctionsList> highRiskSanctions = sanctionsRepository.findHighRiskSanctions();

        for (SanctionsList sanction : highRiskSanctions) {
            String normalized = sanction.getNormalizedName();
            if (normalized == null || normalized.isBlank()) {
                normalized = normalizeName(sanction.getName());
                sanction.setNormalizedName(normalized);
            }

            if (normalized == null || normalized.isEmpty()) {
                logger.debug("Skipping sanctions entry {} due to missing normalized name", sanction.getId());
                continue;
            }

            bkTree.insert(normalized, sanction);
        }

        logger.info("BK-tree initialized with {} high-risk sanctions", highRiskSanctions.size());
    }

    /**
     * Find fuzzy matches for a given name against the sanctions database.
     * Uses a combination of BK-tree and database similarity search for optimal performance.
     */
    @Cacheable(value = "fuzzyMatches", key = "#name + '_' + #threshold")
    public List<MatchResult> findMatches(String name, int threshold) {
        if (name == null || name.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedName = normalizeName(name);
        List<MatchResult> results = new ArrayList<>();

        // First, try BK-tree for high-risk matches (fast)
        if (bkTreeEnabled && bkTree.getSize() > 0) {
            List<MatchResult> bkTreeResults = bkTree.findSimilar(normalizedName, threshold);
            results.addAll(bkTreeResults);
        }

        // Then, use database similarity search for comprehensive coverage
        List<SanctionsList> dbResults = sanctionsRepository.fuzzySearchWithSimilarity(
            normalizedName, (double) threshold / 100.0);

        // Convert to MatchResult and merge with BK-tree results
        for (SanctionsList sanction : dbResults) {
            MatchResult match = new MatchResult(
                sanction,
                calculateSimilarity(normalizedName, sanction.getNormalizedName()),
                "levenshtein"
            );

            // Avoid duplicates
            if (results.stream().noneMatch(r -> r.getSanction().getId().equals(sanction.getId()))) {
                results.add(match);
            }
        }

        // Sort by similarity score (descending)
        results.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));

        // Limit results for performance
        if (results.size() > 50) {
            results = results.subList(0, 50);
        }

        logger.debug("Found {} fuzzy matches for '{}' with threshold {}", results.size(), name, threshold);
        return results;
    }

    /**
     * Find matches using default threshold from configuration.
     */
    public List<MatchResult> findMatches(String name) {
        return findMatches(name, levenshteinThreshold);
    }

    /**
     * Batch processing for multiple names (useful for bulk screening).
     */
    public Map<String, List<MatchResult>> findMatchesBatch(List<String> names, int threshold) {
        Map<String, List<MatchResult>> results = new ConcurrentHashMap<>();

        // Use parallel processing for large batches
        if (names.size() > batchSize) {
            forkJoinPool.submit(() ->
                names.parallelStream().forEach(name -> {
                    List<MatchResult> matches = findMatches(name, threshold);
                    results.put(name, matches);
                })
            ).join();
        } else {
            // Sequential processing for small batches
            for (String name : names) {
                List<MatchResult> matches = findMatches(name, threshold);
                results.put(name, matches);
            }
        }

        return results;
    }

    /**
     * Calculate Levenshtein similarity score between two strings.
     * Returns percentage similarity (0-100).
     */
    public double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }

        if (str1.equals(str2)) {
            return 100.0;
        }

        int distance = levenshteinDistance(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());

        if (maxLength == 0) {
            return 100.0;
        }

        return (1.0 - (double) distance / maxLength) * 100.0;
    }

    /**
     * Calculate Levenshtein (edit) distance between two strings.
     * Uses dynamic programming with space optimization.
     */
    private int levenshteinDistance(String str1, String str2) {
        int len1 = str1.length();
        int len2 = str2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // Use smaller array for space optimization
        if (len1 > len2) {
            String temp = str1;
            str1 = str2;
            str2 = temp;
            len1 = str1.length();
            len2 = str2.length();
        }

        int[] previousRow = new int[len1 + 1];
        for (int i = 0; i <= len1; i++) {
            previousRow[i] = i;
        }

        for (int i = 1; i <= len2; i++) {
            int[] currentRow = new int[len1 + 1];
            currentRow[0] = i;

            for (int j = 1; j <= len1; j++) {
                int cost = (str2.charAt(i - 1) == str1.charAt(j - 1)) ? 0 : 1;

                currentRow[j] = Math.min(
                    Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                    previousRow[j - 1] + cost
                );
            }

            previousRow = currentRow;
        }

        return previousRow[len1];
    }

    /**
     * Normalize a name for fuzzy matching.
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        return name.toUpperCase()
                   .replaceAll("[^A-Z0-9\\s]", "") // Remove punctuation
                   .replaceAll("\\s+", " ")        // Collapse whitespace
                   .trim();
    }

    /**
     * Refresh BK-tree with latest high-risk sanctions.
     */
    public void refreshBKTree() {
        logger.info("Refreshing BK-tree with latest sanctions data");
        this.bkTree = new BKTree();
        initializeBKTree();
    }

    /**
     * Get BK-tree statistics for monitoring.
     */
    public Map<String, Object> getBKTreeStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", bkTreeEnabled);
        stats.put("size", bkTree.getSize());
        stats.put("levenshteinThreshold", levenshteinThreshold);
        stats.put("cacheSize", cacheSize);
        stats.put("batchSize", batchSize);
        return stats;
    }

    /**
     * Result class for fuzzy matching operations.
     */
    public static class MatchResult {
        private final SanctionsList sanction;
        private final double similarityScore;
        private final String algorithm;

        public MatchResult(SanctionsList sanction, double similarityScore, String algorithm) {
            this.sanction = sanction;
            this.similarityScore = similarityScore;
            this.algorithm = algorithm;
        }

        public SanctionsList getSanction() {
            return sanction;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public boolean isAboveThreshold(int threshold) {
            return similarityScore >= threshold;
        }

        @Override
        public String toString() {
            return String.format("MatchResult{sanction='%s', score=%.2f, algorithm='%s'}",
                    sanction.getName(), similarityScore, algorithm);
        }
    }

    /**
     * BK-tree implementation for efficient nearest-neighbor search in metric spaces.
     * Used for fast fuzzy matching of high-risk sanctions.
     */
    private static class BKTree {
        private Node root;
        private int size = 0;

        public void insert(String word, SanctionsList sanction) {
            if (root == null) {
                root = new Node(word, sanction);
                size++;
                return;
            }

            Node current = root;
            while (true) {
                int distance = levenshteinDistance(word, current.word);
                if (distance == 0) {
                    // Duplicate word, skip
                    return;
                }

                Node child = current.children.get(distance);
                if (child == null) {
                    current.children.put(distance, new Node(word, sanction));
                    size++;
                    return;
                }
                current = child;
            }
        }

        public List<MatchResult> findSimilar(String word, int threshold) {
            List<MatchResult> results = new ArrayList<>();
            if (root == null) return results;

            findSimilarRecursive(root, word, threshold, results);
            return results;
        }

        private void findSimilarRecursive(Node node, String word, int threshold, List<MatchResult> results) {
            int distance = levenshteinDistance(word, node.word);
            double similarity = calculateSimilarityScore(word, node.word);

            if (similarity >= threshold) {
                results.add(new MatchResult(node.sanction, similarity, "bk-tree"));
            }

            // Search children within the threshold range
            int minDistance = distance - threshold;
            int maxDistance = distance + threshold;

            for (Map.Entry<Integer, Node> entry : node.children.entrySet()) {
                int childDistance = entry.getKey();
                if (childDistance >= minDistance && childDistance <= maxDistance) {
                    findSimilarRecursive(entry.getValue(), word, threshold, results);
                }
            }
        }

        private double calculateSimilarityScore(String str1, String str2) {
            if (str1.equals(str2)) return 100.0;
            int distance = levenshteinDistance(str1, str2);
            int maxLength = Math.max(str1.length(), str2.length());
            return maxLength == 0 ? 100.0 : (1.0 - (double) distance / maxLength) * 100.0;
        }

        private static int levenshteinDistance(String str1, String str2) {
            // Simplified implementation for BK-tree
            int len1 = str1.length();
            int len2 = str2.length();
            int[][] dp = new int[len1 + 1][len2 + 1];

            for (int i = 0; i <= len1; i++) dp[i][0] = i;
            for (int j = 0; j <= len2; j++) dp[0][j] = j;

            for (int i = 1; i <= len1; i++) {
                for (int j = 1; j <= len2; j++) {
                    int cost = (str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }

            return dp[len1][len2];
        }

        public int getSize() {
            return size;
        }

        private static class Node {
            String word;
            SanctionsList sanction;
            Map<Integer, Node> children = new HashMap<>();

            Node(String word, SanctionsList sanction) {
                this.word = word;
                this.sanction = sanction;
            }
        }
    }
}
