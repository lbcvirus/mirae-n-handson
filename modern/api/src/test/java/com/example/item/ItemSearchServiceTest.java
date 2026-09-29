package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private static ItemSearchCondition condition(String query) {
        return ItemSearchCondition.from(LegacyQueryParams.parse(query));
    }

    @Test
    @DisplayName("search: 0건이면 빈 행과 안내 문구, 행 조회는 하지 않는다")
    void searchReturnsMessageWhenEmpty() {
        ItemSearchCondition condition = condition("unit=Z9-99");
        when(itemRepository.countPublicItems(condition)).thenReturn(0L);

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
        verify(itemRepository, never()).findPublicItems(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("search: 행에 태그를 붙이고, 태그 없는 행은 빈 목록, 안내 문구는 null")
    void searchAttachesTags() {
        ItemSearchCondition condition = condition("page=2");
        when(itemRepository.countPublicItems(condition)).thenReturn(22L);
        when(itemRepository.findPublicItems(condition, 20, 20)).thenReturn(List.of(
            new ItemSearchRow(12, "대분수의 덧셈", "M5-1", 3),
            new ItemSearchRow(3, "소수 한 자리 수의 곱셈", "M5-3", 1)));
        when(itemRepository.findTagNamesByItemIds(List.of(12, 3))).thenReturn(Map.of(12, List.of("계산", "오답률높음")));

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.count()).isEqualTo(22);
        assertThat(response.message()).isNull();
        assertThat(response.rows()).containsExactly(
            new ItemSearchRowResponse(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음")),
            new ItemSearchRowResponse(3, "소수 한 자리 수의 곱셈", "M5-3", 1, List.of()));
    }

    @Test
    @DisplayName("search: 마지막 쪽을 넘는 페이지는 행이 비어도 전체 건수를 돌려주고 안내 문구는 없다")
    void searchBeyondLastPageKeepsCount() {
        ItemSearchCondition condition = condition("page=2");
        when(itemRepository.countPublicItems(condition)).thenReturn(20L);
        when(itemRepository.findPublicItems(condition, 20, 20)).thenReturn(List.of());
        when(itemRepository.findTagNamesByItemIds(anyCollection())).thenReturn(Map.of());

        ItemSearchResponse response = itemSearchService.search(condition);

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isEqualTo(20);
        assertThat(response.message()).isNull();
    }
}
