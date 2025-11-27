package com.example.backend.listener;

import com.example.backend.service.AuditService;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
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

    // Static reference used by JPA listener instances (which are not Spring-managed)
    private static AuditService auditService;

    /**
     * Spring will call this setter on the managed bean instance at startup.
     * This allows JPA-created listener instances to access the Spring-managed AuditService.
     */
    @Autowired
    public void setAuditService(AuditService auditService) {
        AuditEntityListener.auditService = auditService;
    }

    /**
     * Called after an entity is persisted (created).
     * Audits the creation of new entities.
     */
    @PostPersist
    public void postPersist(Object entity) {
        if (auditService == null) {
            logger.warn("AuditService not initialized, skipping audit for {}", entity.getClass().getSimpleName());
            return;
        }
        
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
        if (auditService == null) {
            logger.warn("AuditService not initialized, skipping audit for {}", entity.getClass().getSimpleName());
            return;
        }
        
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
        if (auditService == null) {
            logger.warn("AuditService not initialized, skipping audit for {}", entity.getClass().getSimpleName());
            return;
        }
        
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
     * Gets the actual entity class, handling Hibernate proxies.
     * This is essential for accessing annotations and declared fields correctly.
     * 
     * @param entity The entity object (may be a Hibernate proxy)
     * @return The actual entity class, not the proxy class
     */
    private Class<?> getEntityClass(Object entity) {
        if (entity instanceof HibernateProxy) {
            HibernateProxy proxy = (HibernateProxy) entity;
            LazyInitializer initializer = proxy.getHibernateLazyInitializer();
            return initializer.getPersistentClass();
        }
        return entity.getClass();
    }

    /**
     * Extracts the entity type name from the class.
     * Handles Hibernate proxies by getting the actual entity class name.
     */
    private String getEntityType(Object entity) {
        return getEntityClass(entity).getSimpleName();
    }

    /**
     * Extracts the entity ID using reflection.
     * Assumes entities have a getId() method returning Long.
     * Delegates to extractIdSafely to handle Hibernate proxies.
     */
    private Long getEntityId(Object entity) {
        return extractIdSafely(entity);
    }

    /**
     * Converts entity to a Map for audit payload.
     * Only includes non-null, non-transient fields.
     * Handles associations by extracting IDs to avoid serialization issues.
     * Safely handles Hibernate lazy-loaded proxies without initializing them.
     * 
     * CRITICAL: Uses getEntityClass() to get the real class, not the proxy class,
     * so that annotations (@ManyToOne, @OneToOne) are properly detected.
     */
    private Map<String, Object> entityToMap(Object entity) {
        Map<String, Object> map = new HashMap<>();

        try {
            // Get the actual entity class, not the proxy class
            // This is essential for detecting annotations like @ManyToOne
            Class<?> entityClass = getEntityClass(entity);
            Field[] fields = entityClass.getDeclaredFields();

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
                    // CRITICAL FIX: Check if the field value itself is a Hibernate proxy FIRST
                    // This handles cases where annotation detection might fail or proxies
                    // are present even when not expected. This prevents serialization errors.
                    if (fieldValue instanceof HibernateProxy) {
                        Long relatedId = extractIdSafely(fieldValue);
                        if (relatedId != null) {
                            map.put(fieldName + "Id", relatedId);
                        }
                        // Skip adding the proxy object itself - this is the key fix
                        continue;
                    }
                    
                    // For associations, extract ID instead of full object
                    if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
                        Long relatedId = extractIdSafely(fieldValue);
                        if (relatedId != null) {
                            map.put(fieldName + "Id", relatedId);
                        }
                    } else {
                        map.put(fieldName, fieldValue);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to convert entity to map for {}: {}",
                       entity.getClass().getSimpleName(), e.getMessage());
        }

        return map;
    }

    /**
     * Safely extracts entity ID from an object, handling Hibernate proxies.
     * This prevents serialization issues with lazy-loaded entities and avoids
     * initializing proxies unnecessarily, which could cause performance issues
     * and transaction boundary problems.
     * 
     * @param entity The entity object (may be a Hibernate proxy)
     * @return The entity ID as Long, or null if extraction fails
     */
    private Long extractIdSafely(Object entity) {
        try {
            // Handle Hibernate proxies - extract ID without initializing the entity
            if (entity instanceof HibernateProxy) {
                HibernateProxy proxy = (HibernateProxy) entity;
                LazyInitializer initializer = proxy.getHibernateLazyInitializer();
                
                // Get the ID from the proxy without initializing the entity
                // This is safe and doesn't trigger lazy loading
                Object identifier = initializer.getIdentifier();
                
                if (identifier instanceof Long) {
                    return (Long) identifier;
                } else if (identifier != null) {
                    // Handle other ID types (String, UUID, Integer, etc.)
                    logger.debug("Non-Long identifier type for entity {}: {}", 
                               entity.getClass().getSimpleName(), identifier.getClass());
                    // For non-Long IDs, we could convert or handle differently
                    // For now, we'll try to convert common types
                    if (identifier instanceof Number) {
                        return ((Number) identifier).longValue();
                    }
                    // For other types (UUID, String), we skip to avoid serialization issues
                    return null;
                }
            }
            
            // For non-proxy entities, use reflection to get ID
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            
            if (id instanceof Long) {
                return (Long) id;
            } else if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            
            return null;
            
        } catch (Exception e) {
            logger.warn("Could not extract ID from entity {}: {}", 
                       entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
