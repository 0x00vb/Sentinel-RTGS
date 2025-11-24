package com.example.backend.listener;

import com.example.backend.service.AuditService;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Entity Listener that automatically creates audit logs for entity state changes.
 * Implements SOX compliance requirements for immutable audit trails.
 *
 * This listener captures:
 * - Entity creation (@PostPersist)
 * - Entity updates (@PostUpdate)
 * - Entity deletion (@PostRemove)
 */
@Component
public class AuditEntityListener {

    private static final Logger logger = LoggerFactory.getLogger(AuditEntityListener.class);

    @Autowired
    private AuditService auditService;

    /**
     * Called after an entity is persisted (created).
     * Audits the creation of new entities.
     */
    @PostPersist
    public void postPersist(Object entity) {
        try {
            String entityType = getEntityType(entity);
            Long entityId = getEntityId(entity);

            Map<String, Object> payload = Map.of(
                "action", "CREATED",
                "entity", entityToMap(entity)
            );

            auditService.logAudit(entityType, entityId, "CREATED", payload);

        } catch (Exception e) {
            logger.error("Failed to audit entity creation for {}", entity.getClass().getSimpleName(), e);
            // Don't throw exception to avoid breaking the main transaction
        }
    }

    /**
     * Called after an entity is updated.
     * Audits the modification of existing entities.
     *
     * Note: JPA doesn't provide before/after state in @PostUpdate,
     * so we capture the current state. For full before/after comparison,
     * consider using @PreUpdate with entity state caching.
     */
    @PostUpdate
    public void postUpdate(Object entity) {
        try {
            String entityType = getEntityType(entity);
            Long entityId = getEntityId(entity);

            Map<String, Object> payload = Map.of(
                "action", "UPDATED",
                "entity", entityToMap(entity)
            );

            auditService.logAudit(entityType, entityId, "UPDATED", payload);

        } catch (Exception e) {
            logger.error("Failed to audit entity update for {}", entity.getClass().getSimpleName(), e);
            // Don't throw exception to avoid breaking the main transaction
        }
    }

    /**
     * Called after an entity is removed (deleted).
     * Audits the deletion of entities.
     */
    @PostRemove
    public void postRemove(Object entity) {
        try {
            String entityType = getEntityType(entity);
            Long entityId = getEntityId(entity);

            // For deletions, we can only capture what was deleted
            // since the entity is in detached state
            Map<String, Object> payload = Map.of(
                "action", "DELETED",
                "entityId", entityId,
                "entityType", entityType
            );

            auditService.logAudit(entityType, entityId, "DELETED", payload);

        } catch (Exception e) {
            logger.error("Failed to audit entity deletion for {}", entity.getClass().getSimpleName(), e);
            // Don't throw exception to avoid breaking the main transaction
        }
    }

    /**
     * Extracts the entity type name from the class.
     */
    private String getEntityType(Object entity) {
        return entity.getClass().getSimpleName();
    }

    /**
     * Extracts the entity ID using reflection.
     * Assumes entities have a getId() method returning Long.
     */
    private Long getEntityId(Object entity) {
        try {
            // Try getId() method first
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            return (Long) id;
        } catch (Exception e) {
            logger.warn("Could not extract entity ID for {}: {}", entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Converts entity to a Map for audit payload.
     * Only includes non-null, non-transient fields.
     */
    private Map<String, Object> entityToMap(Object entity) {
        Map<String, Object> map = new HashMap<>();

        try {
            Field[] fields = entity.getClass().getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                // Skip static, transient, and synthetic fields
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    java.lang.reflect.Modifier.isTransient(field.getModifiers()) ||
                    field.isSynthetic()) {
                    continue;
                }

                String fieldName = field.getName();
                Object fieldValue = field.get(entity);

                // Only include non-null values
                if (fieldValue != null) {
                    map.put(fieldName, fieldValue);
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to convert entity to map for {}: {}",
                       entity.getClass().getSimpleName(), e.getMessage());
        }

        return map;
    }
}
