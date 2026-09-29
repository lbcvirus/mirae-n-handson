package com.example.item;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 API. 컨트롤러는 서비스만 호출하고, 예외는 {@code GlobalExceptionHandler} 가 처리한다.
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;
    private final ItemSearchService itemSearchService;

    public ItemController(ItemService itemService, ItemSearchService itemSearchService) {
        this.itemService = itemService;
        this.itemSearchService = itemSearchService;
    }

    /**
     * {@code GET /api/items/search} — 공개 문항 검색. 레거시 {@code /search.php} 와 파라미터 이름이 같다
     * ({@code q, unit, level, tag, sort, dir, page}). {@code level[]} 같은 PHP 배열 표기도 받도록 쿼리스트링 원문을 읽는다.
     */
    @GetMapping("/items/search")
    public ItemSearchResponse searchItems(HttpServletRequest request) {
        LegacyQueryParams params = LegacyQueryParams.parse(request.getQueryString());
        return itemSearchService.search(ItemSearchCondition.from(params));
    }

    /** {@code GET /api/items/{id}} — 문항 단건. */
    @GetMapping("/items/{id}")
    public ItemResponse getItem(@PathVariable Integer id) {
        return itemService.getItem(id);
    }

    /** {@code GET /api/units/{code}/items} — 단원의 공개 문항 목록. */
    @GetMapping("/units/{code}/items")
    public List<ItemResponse> listItemsByUnit(@PathVariable String code) {
        return itemService.listActiveItemsByUnit(code);
    }
}
