package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username for authentication
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username exists (for registration validation)
     */
    boolean existsByUsername(String username);

    /**
     * Find users by role for role-based access control
     */
    List<User> findByRole(User.UserRole role);

    /**
     * Find users by multiple roles
     */
    List<User> findByRoleIn(List<User.UserRole> roles);

    /**
     * Find users who haven't logged in recently (for security monitoring)
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt IS NULL OR u.lastLoginAt < :since")
    List<User> findInactiveUsers(@Param("since") LocalDateTime since);

    /**
     * Update last login time (for security auditing)
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLoginTime(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);

    /**
     * Find compliance officers (for AML workflow assignment)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'COMPLIANCE'")
    List<User> findComplianceOfficers();

    /**
     * Find admin users (for system administration)
     */
    @Query("SELECT u FROM User u WHERE u.role = 'ADMIN'")
    List<User> findAdminUsers();

    /**
     * Find operations users (for dashboard access)
     */
    @Query("SELECT u FROM User u WHERE u.role IN ('ADMIN', 'COMPLIANCE', 'CUSTOMER')")
    List<User> findOperationsUsers();

    /**
     * Count users by role for dashboard metrics
     */
    long countByRole(User.UserRole role);

    /**
     * Find users created within date range (for user growth reporting)
     */
    List<User> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Search users by username containing substring
     */
    List<User> findByUsernameContainingIgnoreCase(String usernamePart);

    /**
     * Get user activity summary (recent logins)
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt IS NOT NULL ORDER BY u.lastLoginAt DESC")
    List<User> findRecentlyActiveUsers();

    /**
     * Find users who need password rotation (based on account age)
     */
    @Query("SELECT u FROM User u WHERE u.createdAt < :cutoffDate")
    List<User> findUsersNeedingPasswordRotation(@Param("cutoffDate") LocalDateTime cutoffDate);
}
