package com.bjtu.offerbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bjtu.offerbot.domain.Offer;
import com.bjtu.offerbot.mapper.OfferMapper;
import com.bjtu.offerbot.service.BusinessException;
import com.bjtu.offerbot.service.OfferService;
import com.bjtu.offerbot.service.dto.BatchCreateResult;
import com.bjtu.offerbot.service.dto.OfferDraft;
import com.bjtu.offerbot.service.dto.OfferSearchCriteria;
import com.bjtu.offerbot.service.dto.PagedResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OfferServiceTests {

    @Autowired
    private OfferService offerService;

    @Autowired
    private OfferMapper offerMapper;

    @Test
    void createsGetsUpdatesReplacesAndDeletesOffer() {
        Offer created = offerService.createOffer(
                new OfferDraft("字节跳动", "北京", "后端实习", "200/天", "本科", "互联网", "实习"),
                "openid-1");

        assertThat(created.getId()).isNotNull();
        assertThat(offerService.getOffer(created.getId())).get().extracting(Offer::getCompany).isEqualTo("字节跳动");

        Offer updated = offerService.updateOffer(created.getId(), Map.of("salary", "250/天", "city", "上海"), "openid-1", false);
        assertThat(updated.getSalary()).isEqualTo("250/天");
        assertThat(updated.getCity()).isEqualTo("上海");

        Offer replaced = offerService.replaceOffer(
                created.getId(),
                new OfferDraft("腾讯", "深圳", "Java后端", "20k*15", "本科", "互联网", "校招"),
                "openid-1",
                false);
        assertThat(replaced.getCompany()).isEqualTo("腾讯");
        assertThat(replaced.getType()).isEqualTo("campus");

        assertThat(offerService.deleteOffer(created.getId(), "openid-1", false)).isTrue();
        assertThat(offerService.getOffer(created.getId())).isEmpty();
    }

    @Test
    void rejectsMutationByNonOwnerAndAllowsAdmin() {
        Offer created = offerService.createOffer(
                new OfferDraft("字节跳动", "北京", "后端实习", "200/天", "本科", "互联网", "实习"),
                "owner-openid");

        assertThatThrownBy(() -> offerService.updateOffer(created.getId(), Map.of("salary", "250/天"), "other-openid", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能修改或删除自己上传的 Offer");
        assertThatThrownBy(() -> offerService.deleteOffer(created.getId(), "other-openid", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能修改或删除自己上传的 Offer");

        Offer adminUpdated = offerService.updateOffer(created.getId(), Map.of("salary", "300/天"), "admin-openid", true);
        assertThat(adminUpdated.getSalary()).isEqualTo("300/天");
        assertThat(offerService.deleteOffer(created.getId(), "admin-openid", true)).isTrue();
    }

    @Test
    void listsWithPaginationAndKeywordSearch() {
        offerService.createOffer(new OfferDraft("字节跳动", "北京", "后端实习", "200/天", "本科", "互联网", "实习"), "u1");
        offerService.createOffer(new OfferDraft("腾讯", "深圳", "Java后端", "20k*15", "本科", "互联网", "校招"), "u1");
        offerService.createOffer(new OfferDraft("美团", "北京", "后端开发", "260/天", "本科", "生活服务", "实习"), "u1");

        PagedResult<Offer> firstPage = offerService.listOffers(OfferSearchCriteria.empty(), 1, 2);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.records()).hasSize(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.records()).extracting(Offer::getCompany).containsExactly("字节跳动", "腾讯");

        PagedResult<Offer> keyword = offerService.listOffers(
                new OfferSearchCriteria("字节", null, null, null, null, null, false), 1, 5);
        assertThat(keyword.records()).singleElement().extracting(Offer::getCompany).isEqualTo("字节跳动");

        PagedResult<Offer> famous = offerService.listOffers(
                new OfferSearchCriteria(null, null, "北京", "后端", null, null, true), 1, 5);
        assertThat(famous.records()).extracting(Offer::getCompany).containsExactlyInAnyOrder("字节跳动", "美团");
    }

    @Test
    void typeSearchMatchesChineseSeedRowsAndNormalizedRows() {
        offerService.createOffer(new OfferDraft("字节跳动", "北京", "后端实习", "200/天", "本科", "互联网", "实习"), "u1");

        Offer rawSeed = new Offer();
        rawSeed.setCompany("字节跳动");
        rawSeed.setCity("北京");
        rawSeed.setPosition("后端开发实习生");
        rawSeed.setSalary("300/天");
        rawSeed.setEducation("本科");
        rawSeed.setIndustry("互联网");
        rawSeed.setType("实习");
        rawSeed.setSourceOpenid("seed:test");
        rawSeed.setCreatedAt(LocalDateTime.now());
        rawSeed.setUpdatedAt(LocalDateTime.now());
        offerMapper.insert(rawSeed);

        PagedResult<Offer> result = offerService.listOffers(
                new OfferSearchCriteria("字节", null, null, null, null, "实习", false), 1, 5);
        assertThat(result.records()).extracting(Offer::getPosition)
                .containsExactlyInAnyOrder("后端实习", "后端开发实习生");
    }

    @Test
    void batchCreateSupportsDashForOptionalFields() {
        BatchCreateResult result = offerService.batchCreateOffers(List.of(
                "字节跳动,北京,后端实习,200/天,本科,互联网,实习",
                "某创业公司,杭州,前端开发,15k*14,-,-,校招"), "openid-1");

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void batchCreateRejectsBadRowsAndRequiredDash() {
        BatchCreateResult result = offerService.batchCreateOffers(List.of(
                "字节跳动,北京,后端实习,200/天,本科,互联网",
                "腾讯,深圳,-,20k*15,本科,互联网,校招"), "openid-1");

        assertThat(result.successCount()).isZero();
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures().get(0)).contains("7 列");
        assertThat(result.failures().get(1)).contains("岗位是必填字段");
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> offerService.createOffer(
                        new OfferDraft("-", "北京", "后端实习", "200/天", null, null, null), "openid-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公司");
    }
}
