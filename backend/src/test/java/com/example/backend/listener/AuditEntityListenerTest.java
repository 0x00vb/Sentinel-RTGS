package com.example.backend.listener;

import com.example.backend.entity.Transfer;
import com.example.backend.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditEntityListener
 */
@ExtendWith(MockitoExtension.class)
class AuditEntityListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditEntityListener auditEntityListener;

    private Transfer testTransfer;

    @BeforeEach
    void setUp() {
        testTransfer = new Transfer();
        testTransfer.setId(1L);
        testTransfer.setAmount(BigDecimal.valueOf(100.00));
        testTransfer.setStatus(Transfer.TransferStatus.PENDING);
        testTransfer.setExternalReference("REF123");
    }

    @Test
    void shouldAuditEntityCreation() throws Exception {
        // Given
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), any());

        // When
        auditEntityListener.postPersist(testTransfer);

        // Then
        verify(auditService).logAudit(eq("Transfer"), eq(1L), eq("CREATED"), any(Map.class));
    }

    @Test
    void shouldAuditEntityUpdate() throws Exception {
        // Given
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), any());

        // When
        auditEntityListener.postUpdate(testTransfer);

        // Then
        verify(auditService).logAudit(eq("Transfer"), eq(1L), eq("UPDATED"), any(Map.class));
    }

    @Test
    void shouldAuditEntityDeletion() throws Exception {
        // Given
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), any());

        // When
        auditEntityListener.postRemove(testTransfer);

        // Then
        verify(auditService).logAudit(eq("Transfer"), eq(1L), eq("DELETED"), any(Map.class));
    }

    @Test
    void shouldHandleAuditFailureGracefully() throws Exception {
        // Given
        doThrow(new RuntimeException("Audit service failure"))
            .when(auditService).logAudit(anyString(), anyLong(), anyString(), any());

        // When & Then - should not throw exception
        assertThatCode(() -> auditEntityListener.postPersist(testTransfer))
            .doesNotThrowAnyException();

        assertThatCode(() -> auditEntityListener.postUpdate(testTransfer))
            .doesNotThrowAnyException();

        assertThatCode(() -> auditEntityListener.postRemove(testTransfer))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldExtractCorrectEntityType() throws Exception {
        // Given
        Transfer transfer = new Transfer();
        Account account = new Account();

        // When
        String transferType = getEntityType(transfer);
        String accountType = getEntityType(account);

        // Then
        assertThat(transferType).isEqualTo("Transfer");
        assertThat(accountType).isEqualTo("Account");
    }

    @Test
    void shouldExtractCorrectEntityId() throws Exception {
        // Given
        Transfer transfer = new Transfer();
        transfer.setId(42L);

        // When
        Long entityId = getEntityId(transfer);

        // Then
        assertThat(entityId).isEqualTo(42L);
    }

    @Test
    void shouldHandleNullEntityIdGracefully() throws Exception {
        // Given
        Transfer transfer = new Transfer();
        // Don't set ID

        // When
        Long entityId = getEntityId(transfer);

        // Then
        assertThat(entityId).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertEntityToMap() throws Exception {
        // Given
        Transfer transfer = new Transfer();
        transfer.setId(1L);
        transfer.setAmount(BigDecimal.valueOf(150.50));
        transfer.setExternalReference("TEST_REF");
        transfer.setStatus(Transfer.TransferStatus.PENDING);

        // When
        Map<String, Object> entityMap = getEntityToMap(transfer);

        // Then
        assertThat(entityMap).isNotNull();
        assertThat(entityMap).containsKey("id");
        assertThat(entityMap).containsKey("amount");
        assertThat(entityMap).containsKey("externalReference");
        assertThat(entityMap).containsKey("status");
        assertThat(entityMap.get("id")).isEqualTo(1L);
        assertThat(entityMap.get("amount")).isEqualTo(BigDecimal.valueOf(150.50));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipNullFieldsInEntityMap() throws Exception {
        // Given
        Transfer transfer = new Transfer();
        transfer.setId(1L);
        // Leave other fields null

        // When
        Map<String, Object> entityMap = getEntityToMap(transfer);

        // Then
        assertThat(entityMap).containsKey("id");
        assertThat(entityMap).doesNotContainKey("amount"); // null field should be excluded
        assertThat(entityMap).doesNotContainKey("externalReference"); // null field should be excluded
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipStaticAndTransientFields() throws Exception {
        // Given - create a test entity with various field types
        Transfer transfer = new Transfer();
        transfer.setId(1L);

        // When
        Map<String, Object> entityMap = getEntityToMap(transfer);

        // Then - should only contain regular instance fields
        assertThat(entityMap).doesNotContainKey("serialVersionUID"); // static field
        assertThat(entityMap.size()).isGreaterThan(0); // but should have some fields
    }

    // Helper methods to access private methods via reflection for testing
    private String getEntityType(Object entity) throws Exception {
        java.lang.reflect.Method method = AuditEntityListener.class.getDeclaredMethod("getEntityType", Object.class);
        method.setAccessible(true);
        return (String) method.invoke(auditEntityListener, entity);
    }

    private Long getEntityId(Object entity) throws Exception {
        java.lang.reflect.Method method = AuditEntityListener.class.getDeclaredMethod("getEntityId", Object.class);
        method.setAccessible(true);
        return (Long) method.invoke(auditEntityListener, entity);
    }

    private Map<String, Object> getEntityToMap(Object entity) throws Exception {
        java.lang.reflect.Method method = AuditEntityListener.class.getDeclaredMethod("entityToMap", Object.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(auditEntityListener, entity);
    }

    // Dummy Account class for testing (simplified version)
    @SuppressWarnings("unused")
    private static class Account {
        private Long id;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
