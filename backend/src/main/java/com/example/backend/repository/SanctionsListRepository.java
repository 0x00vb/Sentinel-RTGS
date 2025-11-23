package com.example.backend.repository;

import com.example.backend.entity.SanctionsList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SanctionsListRepository extends JpaRepository<SanctionsList, Long> {

    /**
     * Find sanctions by exact name match
     */
    List<SanctionsList> findByName(String name);

    /**
     * Find sanctions by normalized name (for fuzzy matching preparation)
     */
    List<SanctionsList> findByNormalizedName(String normalizedName);

    /**
     * Fuzzy search using PostgreSQL full-text search
     * Searches normalized_name using GIN index for performance
     */
    @Query(value = "SELECT * FROM sanctions_list WHERE to_tsvector('simple', normalized_name) @@ plainto_tsquery('simple', :query)",
           nativeQuery = true)
    List<SanctionsList> fuzzySearchByNormalizedName(@Param("query") String query);

    /**
     * Fuzzy search with similarity threshold
     * Uses PostgreSQL similarity function for Levenshtein-like matching
     */
    @Query(value = "SELECT * FROM sanctions_list WHERE similarity(normalized_name, :query) > :threshold ORDER BY similarity(normalized_name, :query) DESC",
           nativeQuery = true)
    List<SanctionsList> fuzzySearchWithSimilarity(@Param("query") String query, @Param("threshold") double threshold);

    /**
     * Find sanctions by source (OFAC, EU, UN, etc.)
     */
    List<SanctionsList> findBySource(String source);

    /**
     * Find sanctions with risk score above threshold
     */
    List<SanctionsList> findByRiskScoreGreaterThanEqual(Integer riskScore);


    /**
     * Find sanctions updated after a specific date
     * Useful for incremental updates from external sources
     */
    List<SanctionsList> findByUpdatedAtAfter(LocalDateTime since);

    /**
     * Count sanctions by source for reporting
     */
    long countBySource(String source);

    /**
     * Get high-risk sanctions (risk_score >= 75)
     */
    @Query("SELECT s FROM SanctionsList s WHERE s.riskScore >= 75 ORDER BY s.riskScore DESC")
    List<SanctionsList> findHighRiskSanctions();

    /**
     * Search sanctions by name containing substring (case-insensitive)
     */
    @Query("SELECT s FROM SanctionsList s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<SanctionsList> searchByNameContaining(@Param("name") String name);

    /**
     * Find sanctions by multiple sources
     */
    List<SanctionsList> findBySourceIn(List<String> sources);

    /**
     * Get sanctions count by risk score ranges for dashboard
     */
    @Query("SELECT COUNT(s) FROM SanctionsList s WHERE s.riskScore BETWEEN :minScore AND :maxScore")
    long countByRiskScoreRange(@Param("minScore") Integer minScore, @Param("maxScore") Integer maxScore);
}
