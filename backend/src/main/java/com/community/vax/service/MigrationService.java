package com.community.vax.service;

import com.community.vax.common.BizException;
import com.community.vax.entity.*;
import com.community.vax.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 迁入儿童接种史核验：
 * 家长上传外地接种本 → 平台识别疫苗名称/剂次/日期/批号为待核验记录 →
 * 医生确认（可纠正模糊项）或驳回 → 重新计算补种计划并重新开放可约时段。
 * 置信度低/无法识别疫苗的记录进入人工提醒队列，防止重复接种或漏种。
 */
@Service
public class MigrationService {

    /** 置信度阈值：低于该值进入“模糊”人工队列 */
    public static final double AMBIGUOUS_THRESHOLD = 0.70;

    private final MigrationDocumentRepository docRepo;
    private final PriorVaccinationRepository priorRepo;
    private final ChildRepository childRepo;
    private final VaccineRepository vaccineRepo;
    private final PlanService planService;
    private final NotificationService notifier;

    public MigrationService(MigrationDocumentRepository docRepo, PriorVaccinationRepository priorRepo,
                            ChildRepository childRepo, VaccineRepository vaccineRepo,
                            PlanService planService, NotificationService notifier) {
        this.docRepo = docRepo;
        this.priorRepo = priorRepo;
        this.childRepo = childRepo;
        this.vaccineRepo = vaccineRepo;
        this.planService = planService;
        this.notifier = notifier;
    }

    public record ParsedRow(String rawVaccine, Integer doseNo, LocalDate date, String batchNo,
                            String clinicName, Double confidence) {}

    /** 上传接种本并识别（演示环境：文本接种本按行解析；图片做模拟 OCR） */
    @Transactional
    public Map<String, Object> uploadAndParse(Long childId, MultipartFile file, SysUser uploader) {
        Child child = childRepo.findById(childId).orElseThrow(() -> new BizException("儿童不存在"));

        MigrationDocument doc = new MigrationDocument();
        doc.setChildId(childId);
        doc.setFileName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        try {
            doc.setData(file.getBytes());
        } catch (IOException e) {
            throw new BizException("文件读取失败");
        }
        doc.setUploadedByUserId(uploader.getId());
        doc.setUploadedByName(uploader.getName());
        doc.setStatus("UPLOADED");

        List<ParsedRow> rows = parse(file, child);
        StringBuilder ocr = new StringBuilder("识别到 ").append(rows.size()).append(" 条接种记录：\n");
        List<PriorVaccination> saved = new ArrayList<>();
        List<PriorVaccination> existing = priorRepo.findByChildIdOrderByVaccinationDateAsc(childId);

        for (ParsedRow row : rows) {
            Vaccine matched = row.rawVaccine() == null ? null
                    : vaccineRepo.findByCode(row.rawVaccine().trim().toUpperCase()).orElse(null);
            String code = matched != null ? matched.getCode() : null;
            String name = matched != null ? matched.getName() : row.rawVaccine();
            double conf = row.confidence() == null ? 0.5 : row.confidence();
            boolean ambiguous = matched == null || conf < AMBIGUOUS_THRESHOLD
                    || row.doseNo() == null || row.batchNo() == null || row.batchNo().isBlank()
                    || "?".equals(row.batchNo());

            // 与既有同疫苗同剂次同日期记录去重，避免重复上传产生重复待核验行
            final String fCode = code;
            boolean dup = existing.stream().anyMatch(p -> Objects.equals(p.getVaccineCode(), fCode)
                    && Objects.equals(p.getDoseNo(), row.doseNo())
                    && Objects.equals(p.getVaccinationDate(), row.date()))
                    || saved.stream().anyMatch(p -> Objects.equals(p.getVaccineCode(), fCode)
                    && Objects.equals(p.getDoseNo(), row.doseNo())
                    && Objects.equals(p.getVaccinationDate(), row.date()));
            if (dup) continue;

            PriorVaccination p = new PriorVaccination();
            p.setChild(child);
            p.setVaccineCode(code);
            p.setVaccineName(name == null || name.isBlank() ? "未能识别的疫苗" : name);
            p.setDoseNo(row.doseNo() == null ? 1 : row.doseNo());
            p.setVaccinationDate(row.date() == null ? LocalDate.now() : row.date());
            p.setBatchNo(("?".equals(row.batchNo()) || row.batchNo() == null) ? null : row.batchNo());
            p.setClinicName(row.clinicName());
            p.setSource("MIGRATED");
            p.setVerifyStatus(ambiguous ? "AMBIGUOUS" : "UNVERIFIED");
            p.setConfidence(Math.round(conf * 100) / 100.0);
            ocr.append("· ").append(p.getVaccineName()).append(" 第").append(p.getDoseNo()).append("剂 ")
                    .append(p.getVaccinationDate()).append(" 批号 ").append(p.getBatchNo() == null ? "模糊" : p.getBatchNo())
                    .append(" 置信度 ").append(p.getConfidence())
                    .append(ambiguous ? "（模糊，需人工）\n" : "（待医生确认）\n");
            saved.add(p);
        }

        doc.setOcrText(ocr.toString());
        doc.setStatus("COMMITTED");
        doc = docRepo.save(doc);
        // 回填文档 ID
        for (PriorVaccination p : saved) {
            p.setMigrationDocId(doc.getId());
        }
        priorRepo.saveAll(saved);

        if (!saved.isEmpty()) {
            planService.regenerate(childId);
            long ambiguousCount = saved.stream().filter(p -> "AMBIGUOUS".equals(p.getVerifyStatus())).count();
            notifier.notifyStaff("MIGRATION", ambiguousCount > 0 ? "URGENT" : "WARN",
                    "外地接种本已上传待核验：" + child.getName(),
                    child.getName() + " 的接种本识别出 " + saved.size() + " 条记录"
                            + (ambiguousCount > 0 ? "，其中 " + ambiguousCount + " 条模糊（疫苗名称/批号不清）已进入人工提醒队列" : "")
                            + "。请核对原件后确认（跳过该剂）或驳回（追加补种）。",
                    child, "MIGRATION_DOC", doc.getId());
        }

        return Map.of("document", doc, "records", saved);
    }

    /**
     * 识别规则：
     * 1) 文本版接种本（.txt / text/*）按行解析：疫苗代码,剂次,yyyy-MM-dd,批号,原单位,置信度
     * 2) 图片/扫描件：模拟 OCR，依据儿童出生日期确定性地产生识别结果（含一条模糊项）
     */
    private List<ParsedRow> parse(MultipartFile file, Child child) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (name.endsWith(".txt") || type.startsWith("text/")) {
            String text;
            try {
                text = new String(file.getBytes());
            } catch (IOException e) {
                throw new BizException("文本读取失败");
            }
            List<ParsedRow> rows = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = line.split(",");
                if (c.length < 3) continue;
                rows.add(new ParsedRow(
                        c[0].trim(),
                        Integer.parseInt(c[1].trim()),
                        LocalDate.parse(c[2].trim()),
                        c.length > 3 ? c[3].trim() : null,
                        c.length > 4 ? c[4].trim() : "外地接种单位",
                        c.length > 5 ? Double.parseDouble(c[5].trim()) : 0.95));
            }
            if (rows.isEmpty()) throw new BizException("未识别到有效接种记录，每行格式：疫苗代码,剂次,日期,批号,单位,置信度");
            return rows;
        }
        // 模拟图片 OCR（演示数据，确定性输出）：出生当天乙肝、次日卡介苗清晰；另一条印章遮挡的模糊记录
        LocalDate b = child.getBirthDate();
        return List.of(
                new ParsedRow("HEPB", 1, b, "HEPB-OCR-" + (child.getId() * 7 % 90 + 10), "外地县医院", 0.98),
                new ParsedRow("BCG", 1, b.plusDays(1), "BCG-OCR-" + (child.getId() * 5 % 90 + 10), "外地县医院", 0.61),
                new ParsedRow("未知疫苗", 1, b.plusMonths(2), "?", "外地接种门诊", 0.33)
        );
    }

    /** 人工提醒队列：全部待核验/模糊记录 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reviewQueue() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String st : List.of("AMBIGUOUS", "UNVERIFIED")) {
            for (PriorVaccination p : priorRepo.findByVerifyStatus(st)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("prior", p);
                row.put("childId", p.getChild().getId());
                row.put("childName", p.getChild().getName());
                row.put("birthDate", p.getChild().getBirthDate());
                if (p.getMigrationDocId() != null) row.put("migrationDocId", p.getMigrationDocId());
                out.add(row);
            }
        }
        return out;
    }

    public record ConfirmRequest(String vaccineCode, Integer doseNo, String vaccinationDate,
                                 String batchNo, String clinicName, String note) {}

    /** 医生确认（可纠正疫苗/剂次/日期/批号）；确认后重算计划、重开后续预约时段 */
    @Transactional
    public PriorVaccination confirm(Long priorId, ConfirmRequest req, SysUser reviewer) {
        PriorVaccination p = priorRepo.findById(priorId).orElseThrow(() -> new BizException("记录不存在"));
        if (req.vaccineCode() != null && !req.vaccineCode().isBlank()) {
            Vaccine v = vaccineRepo.findByCode(req.vaccineCode().trim())
                    .orElseThrow(() -> new BizException("疫苗代码不存在：" + req.vaccineCode()));
            p.setVaccineCode(v.getCode());
            p.setVaccineName(v.getName());
        }
        if (p.getVaccineCode() == null) {
            throw new BizException("该记录未能识别疫苗，请先在“疫苗代码”中纠正为正确疫苗后再确认");
        }
        if (req.doseNo() != null) p.setDoseNo(req.doseNo());
        if (req.vaccinationDate() != null && !req.vaccinationDate().isBlank()) {
            p.setVaccinationDate(LocalDate.parse(req.vaccinationDate()));
        }
        if (req.batchNo() != null) p.setBatchNo(req.batchNo().isBlank() ? null : req.batchNo());
        if (req.clinicName() != null && !req.clinicName().isBlank()) p.setClinicName(req.clinicName());
        p.setVerifyStatus("CONFIRMED");
        p.setConfidence(1.0);
        p.setReviewedByName(reviewer.getName());
        p.setReviewedAt(LocalDateTime.now());
        p.setReviewNote(req.note() == null || req.note().isBlank() ? "接种本原件核对一致" : req.note());
        priorRepo.save(p);

        List<VaccinationPlan> plans = planService.regenerate(p.getChild().getId());
        long skipped = plans.stream().filter(x -> "DONE".equals(x.getStatus())).count();
        long reopened = plans.stream().filter(x -> PlanService.BOOKABLE.contains(x.getStatus())).count();

        Long uid = p.getChild().getGuardian() == null ? null : p.getChild().getGuardian().getUserId();
        if (uid != null) {
            notifier.notifyUser(uid, "MIGRATION", "INFO",
                    "迁入接种记录已核验：" + p.getChild().getName(),
                    p.getVaccineName() + " 第" + p.getDoseNo() + "剂已核对通过并计入程序（本剂不再重复接种）；"
                            + "系统已重新计算补种计划，当前 " + reopened + " 个剂次可在线预约。",
                    p.getChild(), "PRIOR", p.getId());
        }
        notifier.notifyStaff("MIGRATION", "INFO",
                "核验完成：" + p.getChild().getName() + " " + p.getVaccineName(),
                "已确认第" + p.getDoseNo() + "剂并跳过；该儿童共完成 " + skipped + " 剂、" + reopened
                        + " 剂已重新开放预约；追加补种剂次见计划的剂次调整说明。",
                p.getChild(), "PRIOR", p.getId());
        return p;
    }

    /** 驳回：记录不予采信，相应剂次转为追加补种 */
    @Transactional
    public PriorVaccination reject(Long priorId, String note, SysUser reviewer) {
        PriorVaccination p = priorRepo.findById(priorId).orElseThrow(() -> new BizException("记录不存在"));
        p.setVerifyStatus("REJECTED");
        p.setReviewedByName(reviewer.getName());
        p.setReviewedAt(LocalDateTime.now());
        p.setReviewNote(note == null || note.isBlank() ? "接种本记录无法核实，不予采信" : note);
        priorRepo.save(p);
        planService.regenerate(p.getChild().getId());

        Long uid = p.getChild().getGuardian() == null ? null : p.getChild().getGuardian().getUserId();
        if (uid != null) {
            notifier.notifyUser(uid, "MIGRATION", "WARN",
                    "迁入接种记录待补证：" + p.getChild().getName(),
                    p.getVaccineName() + " 第" + p.getDoseNo() + "剂因“" + p.getReviewNote()
                            + "”暂未采信，系统已将该剂加入补种计划，请携带原件到门诊复核或在线预约补种。",
                    p.getChild(), "PRIOR", p.getId());
        }
        return p;
    }

    @Transactional(readOnly = true)
    public List<MigrationDocument> listDocs(Long childId) {
        return docRepo.findByChildIdOrderByCreatedAtDesc(childId);
    }

    @Transactional(readOnly = true)
    public MigrationDocument getDoc(Long docId) {
        return docRepo.findById(docId).orElseThrow(() -> new BizException("接种本文件不存在"));
    }

    @Transactional(readOnly = true)
    public List<PriorVaccination> listRecords(Long childId) {
        return priorRepo.findByChildIdOrderByVaccinationDateAsc(childId).stream()
                .filter(p -> "MIGRATED".equals(p.getSource())).toList();
    }
}
