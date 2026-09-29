package com.example.item;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스. 레거시 검색 화면(legacy/item-bank-php/search.php)의 조회 동작을 옮겼다.
 * 쿼리는 요청마다 최대 3번(건수 · 행 · 태그)이고, 행은 최대 한 페이지(20건)라 입력에 따라 늘지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    /** 0건일 때 안내 문구(search.php:657-659). */
    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * 공개 문항 검색. 없는 단원 · 범위 밖 난이도 · 모르는 태그도 오류가 아니라 0건이다.
     * 전체 건수는 있는데 페이지가 마지막 쪽을 넘으면 행은 비고 안내 문구도 없다(search.php:657).
     */
    public ItemSearchResponse search(ItemSearchCondition condition) {
        long count = itemRepository.countPublicItems(condition);
        if (count == 0) {
            log.debug("item search found no items");
            return new ItemSearchResponse(List.of(), count, NO_RESULT_MESSAGE);
        }
        List<ItemSearchRow> rows =
            itemRepository.findPublicItems(condition, condition.offset(), ItemSearchCondition.PAGE_SIZE);
        Map<Integer, List<String>> tagNames =
            itemRepository.findTagNamesByItemIds(rows.stream().map(ItemSearchRow::id).toList());
        List<ItemSearchRowResponse> responses = rows.stream()
            .map(row -> ItemSearchRowResponse.of(row, tagNames.getOrDefault(row.id(), List.of())))
            .toList();
        log.debug("item search page {} returned {} of {} items", condition.page(), responses.size(), count);
        return new ItemSearchResponse(responses, count, null);
    }
}
