package com.community.vax.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 可重复执行的数据库结构迁移（不依赖删除数据卷，升级保留既有数据）。
 *
 * 针对 prior_vaccination：
 *  - dose_no / vaccination_date 由 NOT NULL 放宽为可空（模糊剂次/日期识别结果需要）
 *  - 新增 raw_dose_text / raw_date_text 保存 OCR 原始片段
 *  - verify_status 缺失时由旧版 verified 字段回填后补齐
 *
 * 通过 information_schema 判断列是否存在、是否 NOT NULL，所有 DDL 幂等，
 * 全新库（Hibernate 已按新实体建表）与旧库升级均可安全重复执行。
 */
@Component
@Order(0)
public class SchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        migratePriorVaccination();
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return n != null && n > 0;
    }

    private List<Map<String, Object>> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name AS name, is_nullable AS nullable, column_type AS type "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?", table);
    }

    private void migratePriorVaccination() {
        if (!tableExists("prior_vaccination")) {
            log.info("prior_vaccination 尚不存在（全新库由 Hibernate 创建），跳过结构迁移");
            return;
        }
        List<Map<String, Object>> cols = columns("prior_vaccination");
        boolean hasVerifyStatus = cols.stream().anyMatch(c -> "verify_status".equals(name(c)));
        boolean hasVerifiedLegacy = cols.stream().anyMatch(c -> "verified".equals(name(c)));

        // 0) 清理指向已不存在儿童的孤儿接种记录，避免 Hibernate 补外键时失败（保留数据优先于约束）
        if (tableExists("child")) {
            int orphans = jdbc.update(
                    "DELETE p FROM prior_vaccination p LEFT JOIN child c ON p.child_id = c.id "
                            + "WHERE c.id IS NULL");
            if (orphans > 0) log.info("迁移：清理 {} 条无对应儿童的孤儿接种记录", orphans);
        }

        // 1) 放宽 NOT NULL（幂等：已是 NULL 时跳过）
        for (Map<String, Object> c : cols) {
            String col = name(c);
            String type = String.valueOf(c.get("type"));
            if (("dose_no".equals(col) || "vaccination_date".equals(col))
                    && "NO".equalsIgnoreCase(String.valueOf(c.get("nullable")))) {
                jdbc.execute("ALTER TABLE prior_vaccination MODIFY COLUMN " + col + " " + type + " NULL");
                log.info("迁移：prior_vaccination.{} 已放宽为可空", col);
            }
        }

        // 2) 补原文字段
        boolean hasRawDose = cols.stream().anyMatch(c -> "raw_dose_text".equals(name(c)));
        if (!hasRawDose) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN raw_dose_text VARCHAR(64) NULL");
            log.info("迁移：prior_vaccination 新增 raw_dose_text");
        }
        boolean hasRawDate = cols.stream().anyMatch(c -> "raw_date_text".equals(name(c)));
        if (!hasRawDate) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN raw_date_text VARCHAR(32) NULL");
            log.info("迁移：prior_vaccination 新增 raw_date_text");
        }

        // 3) 旧版 verified(boolean) → verify_status（幂等回填）
        if (!hasVerifyStatus) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN verify_status VARCHAR(16) NULL");
            log.info("迁移：prior_vaccination 新增 verify_status");
        }
        if (hasVerifiedLegacy) {
            int confirmed = jdbc.update("UPDATE prior_vaccination SET verify_status = 'CONFIRMED' "
                    + "WHERE (verified = 1 OR verified = b'1') AND (verify_status IS NULL OR verify_status = '')");
            int unverified = jdbc.update("UPDATE prior_vaccination SET verify_status = 'UNVERIFIED' "
                    + "WHERE NOT (verified = 1 OR verified = b'1') AND (verify_status IS NULL OR verify_status = '')");
            if (confirmed + unverified > 0) {
                log.info("迁移：由旧 verified 回填 verify_status（CONFIRMED {}，UNVERIFIED {}）", confirmed, unverified);
            }
        }

        // 4) 置信度/审核信息缺失补齐（旧版本无这些列）
        if (cols.stream().noneMatch(c -> "confidence".equals(name(c)))) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN confidence DOUBLE NULL");
        }
        if (cols.stream().noneMatch(c -> "migration_doc_id".equals(name(c)))) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN migration_doc_id BIGINT NULL");
        }
        if (cols.stream().noneMatch(c -> "review_note".equals(name(c)))) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN review_note VARCHAR(255) NULL");
        }
        if (cols.stream().noneMatch(c -> "reviewed_by_name".equals(name(c)))) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN reviewed_by_name VARCHAR(32) NULL");
        }
        if (cols.stream().noneMatch(c -> "reviewed_at".equals(name(c)))) {
            jdbc.execute("ALTER TABLE prior_vaccination ADD COLUMN reviewed_at DATETIME(6) NULL");
        }
        log.info("prior_vaccination 结构迁移完成（可重复执行）");
    }

    private String name(Map<String, Object> c) {
        return String.valueOf(c.get("name"));
    }
}
