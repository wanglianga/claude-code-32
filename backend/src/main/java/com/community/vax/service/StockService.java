package com.community.vax.service;

import com.community.vax.entity.Vaccine;
import com.community.vax.entity.VaccineBatch;
import com.community.vax.repo.VaccineBatchRepository;
import com.community.vax.repo.VaccineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 库存与批号：可发放判断、同组换苗、按效期先出 */
@Service
public class StockService {

    private final VaccineBatchRepository batchRepo;
    private final VaccineRepository vaccineRepo;

    public StockService(VaccineBatchRepository batchRepo, VaccineRepository vaccineRepo) {
        this.batchRepo = batchRepo;
        this.vaccineRepo = vaccineRepo;
    }

    /** 某疫苗当前可发放批号（在效期、冷链正常、有库存），按效期升序 */
    @Transactional(readOnly = true)
    public List<VaccineBatch> availableBatches(String vaccineCode) {
        return batchRepo.findByVaccineCodeOrderByExpiryDateAsc(vaccineCode).stream()
                .filter(VaccineBatch::isAvailable)
                .sorted(Comparator.comparing(VaccineBatch::getExpiryDate))
                .toList();
    }

    /** 同疫苗组内可替代的疫苗（用于“换苗”），返回 [可发放批号] 的疫苗代码列表 */
    @Transactional(readOnly = true)
    public List<String> substitutableVaccineCodes(String vaccineCode) {
        Vaccine v = vaccineRepo.findByCode(vaccineCode).orElse(null);
        if (v == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Vaccine alt : vaccineRepo.findByVaccineGroup(v.getVaccineGroup())) {
            if (!alt.getCode().equals(vaccineCode) && !availableBatches(alt.getCode()).isEmpty()) {
                result.add(alt.getCode());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean inStock(String vaccineCode) {
        return !availableBatches(vaccineCode).isEmpty();
    }

    /** 扣减一支库存（近效期先出），返回实际批号；无可用批号抛业务异常 */
    @Transactional
    public VaccineBatch consume(String vaccineCode) {
        List<VaccineBatch> batches = availableBatches(vaccineCode);
        if (batches.isEmpty()) {
            throw new com.community.vax.common.BizException("疫苗 " + vaccineCode + " 无可用批号（缺货/过期/冷链异常）");
        }
        VaccineBatch batch = batches.get(0);
        batch.setQuantity(batch.getQuantity() - 1);
        return batchRepo.save(batch);
    }

    /** 扣减指定批号一支库存（护士已扫码核验该批号） */
    @Transactional
    public VaccineBatch consumeBatch(String batchNo) {
        VaccineBatch batch = batchRepo.findByBatchNo(batchNo)
                .orElseThrow(() -> new com.community.vax.common.BizException("批号 " + batchNo + " 不存在"));
        if (!batch.isAvailable()) {
            throw new com.community.vax.common.BizException("批号 " + batchNo + " 当前不可发放（过期/冷链中断/库存为 0）");
        }
        batch.setQuantity(batch.getQuantity() - 1);
        return batchRepo.save(batch);
    }

    @Transactional(readOnly = true)
    public List<VaccineBatch> lowStockBatches() {
        return batchRepo.findAll().stream()
                .filter(b -> b.getQuantity() <= b.getSafetyStock())
                .toList();
    }
}
