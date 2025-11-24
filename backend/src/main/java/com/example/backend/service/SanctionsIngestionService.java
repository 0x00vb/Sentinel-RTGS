package com.example.backend.service;

import com.example.backend.entity.SanctionsList;
import com.example.backend.repository.SanctionsListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for ingesting sanctions data from external sources.
 * Supports automated ingestion from OFAC, EU, UN sanctions lists with normalization and deduplication.
 *
 * FR-04: Sanctions lookup against local sanctions DB
 */
@Service
public class SanctionsIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(SanctionsIngestionService.class);

    private final SanctionsListRepository sanctionsRepository;
    private final RestTemplate restTemplate;

    @Value("${compliance.ingestion.ofac-url:#{null}}")
    private String ofacUrl;

    @Value("${compliance.ingestion.eu-sanctions-url:#{null}}")
    private String euSanctionsUrl;

    @Value("${compliance.ingestion.un-sanctions-url:#{null}}")
    private String unSanctionsUrl;

    @Value("${compliance.ingestion.batch-size:1000}")
    private int batchSize;

    @Autowired
    public SanctionsIngestionService(SanctionsListRepository sanctionsRepository) {
        this.sanctionsRepository = sanctionsRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Scheduled job to ingest sanctions data from all configured sources.
     * Runs daily at 2 AM as per devplan configuration.
     */
    @Scheduled(cron = "${compliance.ingestion.schedule:0 0 2 * * *}")
    public void scheduledIngestion() {
        logger.info("Starting scheduled sanctions data ingestion");

        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        // Ingest from each configured source asynchronously
        if (ofacUrl != null && !ofacUrl.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> ingestOfacData()));
        }

        if (euSanctionsUrl != null && !euSanctionsUrl.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> ingestEuData()));
        }

        if (unSanctionsUrl != null && !unSanctionsUrl.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> ingestUnData()));
        }

        // Wait for all ingestions to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                int totalIngested = futures.stream()
                    .mapToInt(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            logger.error("Error during sanctions ingestion", e);
                            return 0;
                        }
                    })
                    .sum();

                logger.info("Completed sanctions ingestion: {} records processed", totalIngested);
            });
    }

    /**
     * Manually trigger sanctions data ingestion (for admin operations).
     */
    @Transactional
    public Map<String, Integer> manualIngestion() {
        logger.info("Starting manual sanctions data ingestion");

        Map<String, Integer> results = new HashMap<>();

        if (ofacUrl != null && !ofacUrl.isEmpty()) {
            results.put("OFAC", ingestOfacData());
        }

        if (euSanctionsUrl != null && !euSanctionsUrl.isEmpty()) {
            results.put("EU", ingestEuData());
        }

        if (unSanctionsUrl != null && !unSanctionsUrl.isEmpty()) {
            results.put("UN", ingestUnData());
        }

        logger.info("Completed manual sanctions ingestion: {}", results);
        return results;
    }

    /**
     * Ingest OFAC Specially Designated Nationals (SDN) list.
     */
    private int ingestOfacData() {
        try {
            logger.info("Ingesting OFAC sanctions data from: {}", ofacUrl);

            String xmlData = restTemplate.getForObject(ofacUrl, String.class);
            if (xmlData == null || xmlData.trim().isEmpty()) {
                logger.error("Received empty OFAC data from {}", ofacUrl);
                return 0;
            }

            List<SanctionsList> ofacEntries = parseOfacXml(xmlData);
            logger.info("Parsed {} OFAC sanctions entries", ofacEntries.size());

            return processSanctionsBatch(ofacEntries, "OFAC");

        } catch (RestClientException e) {
            logger.error("Failed to fetch OFAC data from {}", ofacUrl, e);
            return 0;
        } catch (Exception e) {
            logger.error("Failed to parse OFAC XML data", e);
            return 0;
        }
    }

    /**
     * Ingest EU Financial Sanctions list.
     */
    private int ingestEuData() {
        try {
            logger.info("Ingesting EU sanctions data from: {}", euSanctionsUrl);

            String xmlData = restTemplate.getForObject(euSanctionsUrl, String.class);
            if (xmlData == null || xmlData.trim().isEmpty()) {
                logger.error("Received empty EU data from {}", euSanctionsUrl);
                return 0;
            }

            List<SanctionsList> euEntries = parseEuXml(xmlData);
            logger.info("Parsed {} EU sanctions entries", euEntries.size());

            return processSanctionsBatch(euEntries, "EU");

        } catch (RestClientException e) {
            logger.error("Failed to fetch EU sanctions data from {}", euSanctionsUrl, e);
            return 0;
        } catch (Exception e) {
            logger.error("Failed to parse EU XML data", e);
            return 0;
        }
    }

    /**
     * Ingest UN Security Council sanctions list.
     */
    private int ingestUnData() {
        try {
            logger.info("Ingesting UN sanctions data from: {}", unSanctionsUrl);

            String xmlData = restTemplate.getForObject(unSanctionsUrl, String.class);
            if (xmlData == null || xmlData.trim().isEmpty()) {
                logger.error("Received empty UN data from {}", unSanctionsUrl);
                return 0;
            }

            List<SanctionsList> unEntries = parseUnXml(xmlData);
            logger.info("Parsed {} UN sanctions entries", unEntries.size());

            return processSanctionsBatch(unEntries, "UN");

        } catch (RestClientException e) {
            logger.error("Failed to fetch UN sanctions data from {}", unSanctionsUrl, e);
            return 0;
        } catch (Exception e) {
            logger.error("Failed to parse UN XML data", e);
            return 0;
        }
    }

    /**
     * Process a batch of sanctions entries with normalization and deduplication.
     */
    @Transactional
    private int processSanctionsBatch(List<SanctionsList> entries, String source) {
        int processed = 0;

        for (int i = 0; i < entries.size(); i += batchSize) {
            List<SanctionsList> batch = entries.subList(i, Math.min(i + batchSize, entries.size()));

            // Normalize and deduplicate batch
            List<SanctionsList> normalizedBatch = normalizeAndDeduplicate(batch, source);

            // Bulk save
            sanctionsRepository.saveAll(normalizedBatch);
            processed += normalizedBatch.size();

            logger.debug("Processed batch of {} sanctions from {}", normalizedBatch.size(), source);
        }

        logger.info("Successfully processed {} sanctions from {}", processed, source);
        return processed;
    }

    /**
     * Normalize sanctions data and remove duplicates.
     */
    private List<SanctionsList> normalizeAndDeduplicate(List<SanctionsList> entries, String source) {
        List<SanctionsList> normalized = new ArrayList<>();

        for (SanctionsList entry : entries) {
            // Normalize the name for better matching
            String normalizedName = normalizeName(entry.getName());

            // Check for duplicates based on normalized name and source
            List<SanctionsList> existing = sanctionsRepository.findByNormalizedName(normalizedName);

            boolean isDuplicate = existing.stream()
                .anyMatch(e -> e.getSource().equals(source));

            if (!isDuplicate) {
                entry.setNormalizedName(normalizedName);
                entry.setSource(source);
                entry.setUpdatedAt(LocalDateTime.now());

                // Set risk score based on source
                if (!entry.getSource().equals("OFAC")) {
                    entry.setRiskScore(calculateRiskScore(entry, source));
                }

                normalized.add(entry);
            }
        }

        return normalized;
    }

    /**
     * Normalize a name for fuzzy matching:
     * - Convert to uppercase
     * - Remove punctuation and special characters
     * - Collapse whitespace
     * - Transliterate to ASCII (basic implementation)
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        return name.toUpperCase()
                   .replaceAll("[^A-Z0-9\\s]", "") // Remove punctuation
                   .replaceAll("\\s+", " ")        // Collapse whitespace
                   .trim();
    }

    /**
     * Calculate risk score based on source and other factors.
     */
    private int calculateRiskScore(SanctionsList entry, String source) {
        int baseScore = switch (source) {
            case "OFAC" -> 100;  // Highest risk
            case "UN" -> 90;     // Very high risk
            case "EU" -> 80;     // High risk
            default -> 50;       // Medium risk
        };

        // Could add additional logic based on entry characteristics
        return baseScore;
    }

    /**
     * Parse OFAC SDN XML format.
     * Expected format: <sdnList><sdnEntry><firstName>...</firstName><lastName>...</lastName></sdnEntry>...</sdnList>
     */
    private List<SanctionsList> parseOfacXml(String xmlData) throws Exception {
        List<SanctionsList> entries = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlData)));

        NodeList sdnEntries = doc.getElementsByTagName("sdnEntry");

        for (int i = 0; i < sdnEntries.getLength(); i++) {
            Element entry = (Element) sdnEntries.item(i);

            // Extract name components
            StringBuilder fullName = new StringBuilder();

            // First name
            NodeList firstNames = entry.getElementsByTagName("firstName");
            if (firstNames.getLength() > 0) {
                fullName.append(firstNames.item(0).getTextContent().trim());
            }

            // Last name
            NodeList lastNames = entry.getElementsByTagName("lastName");
            if (lastNames.getLength() > 0) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(lastNames.item(0).getTextContent().trim());
            }

            // If no structured names, try akaList
            if (fullName.length() == 0) {
                NodeList akaList = entry.getElementsByTagName("akaList");
                if (akaList.getLength() > 0) {
                    Element aka = (Element) akaList.item(0);
                    NodeList akaNames = aka.getElementsByTagName("akaName");
                    if (akaNames.getLength() > 0) {
                        fullName.append(akaNames.item(0).getTextContent().trim());
                    }
                }
            }

            if (fullName.length() > 0) {
                SanctionsList sanction = new SanctionsList();
                sanction.setName(fullName.toString());
                sanction.setSource("OFAC");
                sanction.setRiskScore(100); // OFAC SDN entries are high risk
                sanction.setAddedAt(LocalDateTime.now());
                sanction.setUpdatedAt(LocalDateTime.now());
                entries.add(sanction);
            }
        }

        return entries;
    }

    /**
     * Parse EU Financial Sanctions XML format.
     */
    private List<SanctionsList> parseEuXml(String xmlData) throws Exception {
        List<SanctionsList> entries = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlData)));

        // EU sanctions XML structure - look for sanction entities
        NodeList sanctionEntities = doc.getElementsByTagName("sanctionEntity");

        for (int i = 0; i < sanctionEntities.getLength(); i++) {
            Element entity = (Element) sanctionEntities.item(i);

            // Extract name from nameAlias or other name fields
            NodeList names = entity.getElementsByTagName("nameAlias");
            if (names.getLength() == 0) {
                names = entity.getElementsByTagName("name");
            }

            for (int j = 0; j < names.getLength(); j++) {
                Element nameElement = (Element) names.item(j);
                String name = nameElement.getTextContent().trim();

                if (!name.isEmpty()) {
                    SanctionsList sanction = new SanctionsList();
                    sanction.setName(name);
                    sanction.setSource("EU");
                    sanction.setRiskScore(80); // EU sanctions are high risk
                    sanction.setAddedAt(LocalDateTime.now());
                    sanction.setUpdatedAt(LocalDateTime.now());
                    entries.add(sanction);
                }
            }
        }

        return entries;
    }

    /**
     * Parse UN Consolidated Sanctions XML format.
     */
    private List<SanctionsList> parseUnXml(String xmlData) throws Exception {
        List<SanctionsList> entries = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlData)));

        // UN sanctions XML structure - look for individual or entity names
        NodeList individuals = doc.getElementsByTagName("INDIVIDUAL");
        NodeList entities = doc.getElementsByTagName("ENTITY");

        // Parse individuals
        for (int i = 0; i < individuals.getLength(); i++) {
            Element individual = (Element) individuals.item(i);

            // Extract name components
            StringBuilder fullName = new StringBuilder();

            NodeList firstNames = individual.getElementsByTagName("FIRST_NAME");
            NodeList secondNames = individual.getElementsByTagName("SECOND_NAME");
            NodeList thirdNames = individual.getElementsByTagName("THIRD_NAME");

            if (firstNames.getLength() > 0) {
                fullName.append(firstNames.item(0).getTextContent().trim());
            }
            if (secondNames.getLength() > 0) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(secondNames.item(0).getTextContent().trim());
            }
            if (thirdNames.getLength() > 0) {
                if (fullName.length() > 0) fullName.append(" ");
                fullName.append(thirdNames.item(0).getTextContent().trim());
            }

            if (fullName.length() > 0) {
                SanctionsList sanction = new SanctionsList();
                sanction.setName(fullName.toString());
                sanction.setSource("UN");
                sanction.setRiskScore(90); // UN sanctions are very high risk
                sanction.setAddedAt(LocalDateTime.now());
                sanction.setUpdatedAt(LocalDateTime.now());
                entries.add(sanction);
            }
        }

        // Parse entities
        for (int i = 0; i < entities.getLength(); i++) {
            Element entity = (Element) entities.item(i);

            NodeList entityNames = entity.getElementsByTagName("ENTITY_NAME");
            for (int j = 0; j < entityNames.getLength(); j++) {
                String name = entityNames.item(j).getTextContent().trim();

                if (!name.isEmpty()) {
                    SanctionsList sanction = new SanctionsList();
                    sanction.setName(name);
                    sanction.setSource("UN");
                    sanction.setRiskScore(90);
                    sanction.setAddedAt(LocalDateTime.now());
                    sanction.setUpdatedAt(LocalDateTime.now());
                    entries.add(sanction);
                }
            }
        }

        return entries;
    }


    /**
     * Get statistics about the sanctions database.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalSanctions", sanctionsRepository.count());
        stats.put("ofacCount", sanctionsRepository.countBySource("OFAC"));
        stats.put("euCount", sanctionsRepository.countBySource("EU"));
        stats.put("unCount", sanctionsRepository.countBySource("UN"));
        stats.put("highRiskCount", sanctionsRepository.findHighRiskSanctions().size());

        // Get risk score distribution
        Map<String, Long> riskDistribution = new HashMap<>();
        riskDistribution.put("high", sanctionsRepository.countByRiskScoreRange(75, 100));
        riskDistribution.put("medium", sanctionsRepository.countByRiskScoreRange(50, 74));
        riskDistribution.put("low", sanctionsRepository.countByRiskScoreRange(0, 49));
        stats.put("riskDistribution", riskDistribution);

        return stats;
    }
}
