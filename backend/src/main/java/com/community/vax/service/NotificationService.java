package com.community.vax.service;

import com.community.vax.entity.Child;
import com.community.vax.entity.Notification;
import com.community.vax.entity.SysUser;
import com.community.vax.repo.NotificationRepository;
import com.community.vax.repo.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final SysUserRepository userRepo;

    public NotificationService(NotificationRepository notificationRepo, SysUserRepository userRepo) {
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
    }

    /** 向某角色全员广播（按角色未读去重） */
    @Transactional
    public Notification notifyRole(String role, String type, String level, String title, String content,
                                   Child child, String refType, Long refId) {
        Long ref = refId == null ? -1L : refId;
        if (notificationRepo.existsByTypeAndRefTypeAndRefIdAndTargetRoleAndIsReadFalse(type, refType, ref, role)) {
            return null;
        }
        Notification n = base(type, level, title, content, child, refType, refId);
        n.setTargetRole(role);
        return notificationRepo.save(n);
    }

    /** 向指定用户推送（家长账号走个人通道，按用户未读去重） */
    @Transactional
    public Notification notifyUser(Long userId, String type, String level, String title, String content,
                                   Child child, String refType, Long refId) {
        SysUser u = userRepo.findById(userId).orElse(null);
        if (u == null) return null;
        Long ref = refId == null ? -1L : refId;
        if (notificationRepo.existsByTypeAndRefTypeAndRefIdAndTargetUserIdAndIsReadFalse(type, refType, ref, userId)) {
            return null;
        }
        Notification n = base(type, level, title, content, child, refType, refId);
        n.setTargetRole(u.getRole().name());
        n.setTargetUserId(userId);
        return notificationRepo.save(n);
    }

    /** 按业务要求：状态同时推给医生、护士、随访人员 */
    @Transactional
    public void notifyStaff(String type, String level, String title, String content, Child child,
                            String refType, Long refId) {
        notifyRole("DOCTOR", type, level, title, content, child, refType, refId);
        notifyRole("NURSE", type, level, title, content, child, refType, refId);
        notifyRole("FOLLOWUP", type, level, title, content, child, refType, refId);
    }

    private Notification base(String type, String level, String title, String content,
                              Child child, String refType, Long refId) {
        Notification n = new Notification();
        n.setType(type);
        n.setLevel(level);
        n.setTitle(title);
        n.setContent(content);
        if (child != null) {
            n.setChildId(child.getId());
            n.setChildName(child.getName());
        }
        n.setRefType(refType);
        n.setRefId(refId);
        return n;
    }

    @Transactional(readOnly = true)
    public List<Notification> listFor(SysUser user) {
        List<Notification> all = new ArrayList<>();
        all.addAll(notificationRepo.findByTargetRoleAndTargetUserIdIsNullOrderByCreatedAtDesc(user.getRole().name()));
        all.addAll(notificationRepo.findByTargetUserIdOrderByCreatedAtDesc(user.getId()));
        all.sort(Comparator.comparing(Notification::getCreatedAt).reversed());
        return all.size() > 100 ? all.subList(0, 100) : all;
    }

    @Transactional(readOnly = true)
    public long unreadCount(SysUser user) {
        return notificationRepo.countByTargetRoleAndTargetUserIdIsNullAndIsReadFalse(user.getRole().name())
                + notificationRepo.countByTargetUserIdAndIsReadFalse(user.getId());
    }

    @Transactional
    public void markRead(Long id) {
        notificationRepo.findById(id).ifPresent(n -> n.setIsRead(true));
    }

    @Transactional
    public void markAllRead(SysUser user) {
        listFor(user).stream().filter(n -> !n.getIsRead()).forEach(n -> n.setIsRead(true));
    }
}
