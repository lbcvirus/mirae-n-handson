package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** 저장소 슬라이스 — H2(MariaDB 모드)에 실제 SQL 을 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UnitRepository unitRepository;

    private Unit fraction;

    @BeforeEach
    void seed() {
        fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 문항", 5, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByUnitCodeAndStatus: 공개 문항만, level DESC · id ASC, 태그 함께 로드")
    void findsActiveItemsOrderedWithTags() {
        List<Item> items = itemRepository.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE);

        assertThat(items).hasSize(3);
        assertThat(items).extracting(Item::getTitle)
            .containsExactly("난이도 5 분수", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(items).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo("A"));
        assertThat(items.get(1).getTags()).extracting(Tag::getName).containsExactly("계산", "문장제");
    }

    @Test
    @DisplayName("findWithDetailsById: 단원 · 태그가 한 번에 로드된다(지연 로딩 아님)")
    void findWithDetailsLoadsUnitAndTags() {
        Integer id = itemRepository.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE).get(1).getId();
        entityManager.clear();

        Item item = itemRepository.findWithDetailsById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(item.getUnit())).isTrue();
        assertThat(Hibernate.isInitialized(item.getTags())).isTrue();
        assertThat(item.getUnit().getCode()).isEqualTo("M5-1");
        assertThat(item.getTags()).hasSize(2);
    }

    @Test
    @DisplayName("countByUnitIdAndStatus: 상태별 문항 수")
    void countsByStatus() {
        assertThat(itemRepository.countByUnitIdAndStatus(fraction.getId(), ItemStatus.ACTIVE)).isEqualTo(3);
        assertThat(itemRepository.countByUnitIdAndStatus(fraction.getId(), ItemStatus.DELETED)).isEqualTo(1);
        assertThat(unitRepository.findByCode("M5-3")).isPresent();
    }

    private static ItemSearchCondition condition(String query) {
        return ItemSearchCondition.from(LegacyQueryParams.parse(query));
    }

    private List<String> searchTitles(String query) {
        ItemSearchCondition condition = condition(query);
        return itemRepository.findPublicItems(condition, condition.offset(), ItemSearchCondition.PAGE_SIZE).stream()
            .map(ItemSearchRow::title)
            .toList();
    }

    @Test
    @DisplayName("검색 기본: 공개 문항 중 난이도 5 를 뺀 문항, level DESC · id ASC")
    void searchDefaultExcludesLevelFiveAndNonActive() {
        assertThat(itemRepository.countPublicItems(condition(null))).isEqualTo(3);
        assertThat(searchTitles(null)).containsExactly("분수 문장제", "분모가 같은 분수의 덧셈", "소수 곱셈");
    }

    @Test
    @DisplayName("검색 조건은 AND: 키워드(제목 · 지문) + 단원 + 태그 정확 일치")
    void searchCombinesConditions() {
        String query = "q=%EB%B6%84%EC%88%98&unit=M5-1&tag=%EB%AC%B8%EC%9E%A5%EC%A0%9C";

        assertThat(itemRepository.countPublicItems(condition(query))).isEqualTo(1);
        assertThat(searchTitles(query)).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("검색 난이도 지정: 5 도 찾고, 검수중 · 삭제 문항은 빠진다. 없는 단원은 0건")
    void searchByLevelAndUnknownUnit() {
        assertThat(searchTitles("level=5")).containsExactly("난이도 5 분수");
        assertThat(searchTitles("level=3")).isEmpty();
        assertThat(itemRepository.countPublicItems(condition("unit=Z9-99"))).isZero();
    }

    @Test
    @DisplayName("검색 정렬 · 페이지: unit 정렬의 2차 정렬은 level DESC, 마지막 쪽 너머는 빈 행")
    void searchSortsAndPages() {
        assertThat(searchTitles("sort=unit&dir=desc"))
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(searchTitles("sort=title")).containsExactly("분모가 같은 분수의 덧셈", "분수 문장제", "소수 곱셈");
        assertThat(searchTitles("page=2")).isEmpty();
    }

    @Test
    @DisplayName("findTagNamesByItemIds: 문항별 태그 이름을 태그 id 순으로, 태그 없는 문항은 키 없음")
    void findsTagNamesInTagIdOrder() {
        ItemSearchCondition condition = condition(null);
        List<ItemSearchRow> rows = itemRepository.findPublicItems(condition, 0, ItemSearchCondition.PAGE_SIZE);
        Integer wordItemId = rows.get(0).id();
        Integer noTagItemId = rows.get(2).id();

        Map<Integer, List<String>> tags =
            itemRepository.findTagNamesByItemIds(rows.stream().map(ItemSearchRow::id).toList());

        assertThat(tags.get(wordItemId)).containsExactly("계산", "문장제");
        assertThat(tags).doesNotContainKey(noTagItemId);
        assertThat(itemRepository.findTagNamesByItemIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("검색 created 정렬: 방향이 없으면 최신순, asc 면 오래된 순, 등록 시각이 같으면 어느 방향이든 id ASC")
    void searchSortsByCreatedAt() {
        Unit multiply = entityManager.persist(ItemFixtures.unit(2, "M5-2", "분수의 곱셈", 5));
        entityManager.persist(ItemFixtures.item(
            null, multiply, "최근 문항 A", 2, ItemStatus.ACTIVE, ItemFixtures.SEED_TIME.plusDays(1)));
        entityManager.persist(ItemFixtures.item(
            null, multiply, "오래된 문항", 3, ItemStatus.ACTIVE, ItemFixtures.SEED_TIME.minusDays(1)));
        entityManager.persist(ItemFixtures.item(
            null, multiply, "최근 문항 B", 1, ItemStatus.ACTIVE, ItemFixtures.SEED_TIME.plusDays(1)));
        entityManager.flush();
        entityManager.clear();

        assertThat(searchTitles("unit=M5-2&sort=created"))
            .containsExactly("최근 문항 A", "최근 문항 B", "오래된 문항");
        assertThat(searchTitles("unit=M5-2&sort=created&dir=desc"))
            .containsExactly("최근 문항 A", "최근 문항 B", "오래된 문항");
        assertThat(searchTitles("unit=M5-2&sort=created&dir=asc"))
            .containsExactly("오래된 문항", "최근 문항 A", "최근 문항 B");
    }

    @Test
    @DisplayName("검색 id · level · title 정렬 방향과 지문(stem) 키워드 일치가 실제 쿼리에서 동작한다")
    void searchSortsByIdLevelTitleAndMatchesStem() {
        assertThat(searchTitles("sort=id&dir=desc"))
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(searchTitles("sort=level&dir=asc"))
            .containsExactly("소수 곱셈", "분모가 같은 분수의 덧셈", "분수 문장제");
        assertThat(searchTitles("sort=title&dir=desc"))
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
        // 지문은 "<제목> 문제 본문" 이라 제목에 없는 단어로도 찾는다
        assertThat(itemRepository.countPublicItems(condition("q=%EB%B3%B8%EB%AC%B8"))).isEqualTo(3);
    }
}
