package com.community.vax.repo;

import com.community.vax.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByTargetRoleAndTargetUserIdIsNullOrderByCreatedAtDesc(String role);
    List<Notification> findByTargetUserIdOrderByCreatedAtDesc(Long userId);
    long countByTargetRoleAndTargetUserIdIsNullAndIsReadFalse(String role);
    long countByTargetUserIdAndIsReadFalse(Long userId);
    boolean existsByChildIdAndTypeAndTargetRoleAndIsReadFalse(Long childId, String type, String targetRole);
    boolean existsByTypeAndRefTypeAndRefIdAndIsReadFalse(String type, String refType, Long refId);
    boolean existsByTypeAndRefTypeAndRefIdAndTargetRoleAndIsReadFalse(String type, String refType, Long refId, String targetRole);
    boolean existsByTypeAndRefTypeAndRefIdAndTargetUserIdAndIsReadFalse(String type, String refType, Long refId, Long targetUserId);
}
