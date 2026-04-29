package com.bjtu.offerbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtu.offerbot.domain.Offer;
import com.bjtu.offerbot.mapper.OfferMapper;
import com.bjtu.offerbot.service.dto.BatchCreateResult;
import com.bjtu.offerbot.service.dto.OfferDraft;
import com.bjtu.offerbot.service.dto.OfferSearchCriteria;
import com.bjtu.offerbot.service.dto.PagedResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OfferService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 5;
    private static final int MAX_SIZE = 10;
    private static final Set<String> FAMOUS_COMPANIES = Set.of(
            "字节跳动", "腾讯", "阿里巴巴", "美团", "京东", "百度", "网易", "快手", "小米", "华为");

    private final OfferMapper offerMapper;

    public OfferService(OfferMapper offerMapper) {
        this.offerMapper = offerMapper;
    }

    @Transactional
    public Offer createOffer(OfferDraft draft, String sourceOpenid) {
        validateDraft(draft);
        if (!StringUtils.hasText(sourceOpenid)) {
            throw new BusinessException("缺少上传用户 openid。");
        }

        LocalDateTime now = LocalDateTime.now();
        Offer offer = new Offer();
        applyDraft(offer, draft);
        offer.setSourceOpenid(sourceOpenid);
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        offerMapper.insert(offer);
        return offer;
    }

    public Optional<Offer> getOffer(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("编号必须是大于 0 的整数。");
        }
        return Optional.ofNullable(offerMapper.selectById(id));
    }

    public PagedResult<Offer> listOffers(OfferSearchCriteria criteria, Integer page, Integer size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        LambdaQueryWrapper<Offer> wrapper = buildQuery(criteria == null ? OfferSearchCriteria.empty() : criteria);
        wrapper.orderByAsc(Offer::getId);

        List<Offer> allRecords = offerMapper.selectList(wrapper);
        int total = allRecords.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) normalizedSize);
        int from = Math.min((normalizedPage - 1) * normalizedSize, total);
        int to = Math.min(from + normalizedSize, total);
        return new PagedResult<>(allRecords.subList(from, to), total, normalizedPage, normalizedSize, totalPages);
    }

    @Transactional
    public Offer updateOffer(Long id, Map<String, String> patch) {
        Offer offer = getOffer(id).orElseThrow(() -> new BusinessException("没有找到编号=" + id + " 的记录。"));
        if (patch == null || patch.isEmpty()) {
            throw new BusinessException("请提供至少一个要更新的字段。");
        }

        boolean changed = applyPatch(offer, patch);
        if (!changed) {
            throw new BusinessException("请提供至少一个要更新的字段。");
        }

        offer.setUpdatedAt(LocalDateTime.now());
        offerMapper.updateById(offer);
        return offer;
    }

    @Transactional
    public Offer replaceOffer(Long id, OfferDraft draft) {
        Offer offer = getOffer(id).orElseThrow(() -> new BusinessException("没有找到编号=" + id + " 的记录。"));
        validateDraft(draft);
        applyDraft(offer, draft);
        offer.setUpdatedAt(LocalDateTime.now());
        offerMapper.updateById(offer);
        return offer;
    }

    @Transactional
    public boolean deleteOffer(Long id) {
        getOffer(id).orElseThrow(() -> new BusinessException("没有找到编号=" + id + " 的记录。"));
        return offerMapper.deleteById(id) > 0;
    }

    @Transactional
    public BatchCreateResult batchCreateOffers(List<String> rows, String sourceOpenid) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("批量上传内容为空。");
        }

        int successCount = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            int lineNumber = i + 2;
            try {
                OfferDraft draft = parseBatchRow(row, lineNumber);
                createOffer(draft, sourceOpenid);
                successCount++;
            } catch (BusinessException e) {
                failures.add("第 " + lineNumber + " 行失败：" + e.getMessage());
            }
        }
        return new BatchCreateResult(successCount, failures);
    }

    private LambdaQueryWrapper<Offer> buildQuery(OfferSearchCriteria criteria) {
        LambdaQueryWrapper<Offer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(criteria.keyword())) {
            String keyword = criteria.keyword().trim();
            wrapper.and(w -> w.like(Offer::getCompany, keyword)
                    .or()
                    .like(Offer::getCity, keyword)
                    .or()
                    .like(Offer::getPosition, keyword)
                    .or()
                    .like(Offer::getSalary, keyword)
                    .or()
                    .like(Offer::getIndustry, keyword)
                    .or()
                    .like(Offer::getType, keyword));
        }
        if (StringUtils.hasText(criteria.company())) {
            wrapper.like(Offer::getCompany, criteria.company().trim());
        }
        if (StringUtils.hasText(criteria.city())) {
            wrapper.like(Offer::getCity, criteria.city().trim());
        }
        if (StringUtils.hasText(criteria.position())) {
            wrapper.like(Offer::getPosition, criteria.position().trim());
        }
        if (StringUtils.hasText(criteria.industry())) {
            wrapper.like(Offer::getIndustry, criteria.industry().trim());
        }
        if (StringUtils.hasText(criteria.type())) {
            String rawType = criteria.type().trim();
            String normalizedType = normalizeOptional(rawType);
            wrapper.and(w -> w.eq(Offer::getType, normalizedType).or().eq(Offer::getType, rawType));
        }
        if (criteria.famousOnly()) {
            wrapper.in(Offer::getCompany, FAMOUS_COMPANIES);
        }
        return wrapper;
    }

    private OfferDraft parseBatchRow(String row, int lineNumber) {
        if (!StringUtils.hasText(row)) {
            throw new BusinessException("空行。");
        }
        String[] parts = row.split(",", -1);
        if (parts.length != 7) {
            throw new BusinessException("批量上传每行必须是 7 列。格式：公司,城市,岗位,薪资,学历,行业,类型");
        }

        OfferDraft draft = new OfferDraft(
                parts[0].trim(),
                parts[1].trim(),
                parts[2].trim(),
                parts[3].trim(),
                normalizeOptional(parts[4]),
                normalizeOptional(parts[5]),
                normalizeOptional(parts[6]));

        if (!hasRequiredValue(draft.company())) {
            throw new BusinessException("公司是必填字段，不能使用 -。");
        }
        if (!hasRequiredValue(draft.city())) {
            throw new BusinessException("城市是必填字段，不能使用 -。");
        }
        if (!hasRequiredValue(draft.position())) {
            throw new BusinessException("岗位是必填字段，不能使用 -。");
        }
        if (!hasRequiredValue(draft.salary())) {
            throw new BusinessException("薪资是必填字段，不能使用 -。");
        }
        return draft;
    }

    private void validateDraft(OfferDraft draft) {
        if (draft == null) {
            throw new BusinessException("缺少 Offer 信息。");
        }

        List<String> missing = new ArrayList<>();
        if (!hasRequiredValue(draft.company())) {
            missing.add("公司");
        }
        if (!hasRequiredValue(draft.city())) {
            missing.add("城市");
        }
        if (!hasRequiredValue(draft.position())) {
            missing.add("岗位");
        }
        if (!hasRequiredValue(draft.salary())) {
            missing.add("薪资");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("缺少必填字段：" + String.join("、", missing));
        }
    }

    private boolean applyPatch(Offer offer, Map<String, String> patch) {
        boolean changed = false;
        changed |= applyIfPresent(patch, "company", offer::setCompany, true);
        changed |= applyIfPresent(patch, "city", offer::setCity, true);
        changed |= applyIfPresent(patch, "position", offer::setPosition, true);
        changed |= applyIfPresent(patch, "salary", offer::setSalary, true);
        changed |= applyIfPresent(patch, "education", offer::setEducation, false);
        changed |= applyIfPresent(patch, "industry", offer::setIndustry, false);
        changed |= applyIfPresent(patch, "type", offer::setType, false);
        return changed;
    }

    private boolean applyIfPresent(
            Map<String, String> patch, String key, java.util.function.Consumer<String> setter, boolean required) {
        if (!patch.containsKey(key)) {
            return false;
        }
        String value = required ? requirePatchValue(key, patch.get(key)) : normalizeOptional(patch.get(key));
        setter.accept(value);
        return true;
    }

    private String requirePatchValue(String key, String value) {
        if (!hasRequiredValue(value)) {
            throw new BusinessException(toChineseField(key) + "是必填字段，不能使用 -。");
        }
        return value.trim();
    }

    private void applyDraft(Offer offer, OfferDraft draft) {
        offer.setCompany(draft.company().trim());
        offer.setCity(draft.city().trim());
        offer.setPosition(draft.position().trim());
        offer.setSalary(draft.salary().trim());
        offer.setEducation(normalizeOptional(draft.education()));
        offer.setIndustry(normalizeOptional(draft.industry()));
        offer.setType(normalizeOptional(draft.type()));
    }

    private boolean hasRequiredValue(String value) {
        return StringUtils.hasText(value) && !"-".equals(value.trim());
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value) || "-".equals(value.trim())) {
            return null;
        }
        String trimmed = value.trim();
        return switch (trimmed) {
            case "实习" -> "internship";
            case "校招" -> "campus";
            case "社招" -> "fulltime";
            default -> trimmed;
        };
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new BusinessException("页码/page 必须是大于等于 1 的整数。");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1) {
            throw new BusinessException("每页/size 必须是大于等于 1 的整数。");
        }
        return Math.min(size, MAX_SIZE);
    }

    private String toChineseField(String key) {
        return switch (key) {
            case "company" -> "公司";
            case "city" -> "城市";
            case "position" -> "岗位";
            case "salary" -> "薪资";
            default -> key;
        };
    }
}
