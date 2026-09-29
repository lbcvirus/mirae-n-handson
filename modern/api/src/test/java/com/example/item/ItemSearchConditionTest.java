package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class ItemSearchConditionTest {

    private static ItemSearchCondition parse(String query) {
        return ItemSearchCondition.from(LegacyQueryParams.parse(query));
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없음 · 기본 난이도 범위 · 기본 정렬 · 1페이지")
    void defaultsWhenEmpty() {
        ItemSearchCondition condition = parse(null);

        assertThat(condition.keyword()).isNull();
        assertThat(condition.unitCode()).isNull();
        assertThat(condition.level()).isNull();
        assertThat(condition.tag()).isNull();
        assertThat(condition.sort()).isEqualTo(ItemSearchSort.DEFAULT);
        assertThat(condition.page()).isEqualTo(1);
        assertThat(condition.offset()).isZero();
    }

    @Test
    @DisplayName("키워드 · 태그는 앞뒤 공백을 빼고 각각 100자 · 50자까지만 쓴다")
    void trimsAndTruncatesKeywordAndTag() {
        assertThat(ItemSearchCondition.keyword("  ")).isNull();
        assertThat(ItemSearchCondition.keyword(" 분수 ")).isEqualTo("분수");
        assertThat(ItemSearchCondition.keyword("가".repeat(100) + "나")).isEqualTo("가".repeat(100));
        assertThat(ItemSearchCondition.tag("가".repeat(51))).isEqualTo("가".repeat(50));
        assertThat(ItemSearchCondition.unitCode(" m5-1 ")).isEqualTo("m5-1");
    }

    @Test
    @DisplayName("PHP trim 은 \\f 를 지우지 않는다")
    void phpTrimKeepsFormFeed() {
        assertThat(ItemSearchCondition.phpTrim("\t\n a \u000B\0")).isEqualTo("a");
        assertThat(ItemSearchCondition.phpTrim("\fa")).isEqualTo("\fa");
    }

    @Test
    @DisplayName("난이도는 PHP (int) 변환 값으로 비교한다: 3abc → 3, abc → 0, 1e3 → 1000")
    void levelUsesPhpIntval() {
        assertThat(ItemSearchCondition.level("")).isNull();
        assertThat(ItemSearchCondition.level("3")).isEqualTo(3);
        assertThat(ItemSearchCondition.level("3abc")).isEqualTo(3);
        assertThat(ItemSearchCondition.level("3.7")).isEqualTo(3);
        assertThat(ItemSearchCondition.level("abc")).isZero();
        assertThat(ItemSearchCondition.level("1e3")).isEqualTo(1000);
        assertThat(ItemSearchCondition.level("-2")).isEqualTo(-2);
        assertThat(ItemSearchCondition.level("99999999999999999999")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("페이지는 숫자로만 된 원문만 받고 1~999 로 보정한다")
    void clampsPage() {
        assertThat(ItemSearchCondition.page("")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("-5")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("0")).isEqualTo(1);
        assertThat(ItemSearchCondition.page(" 2")).isEqualTo(1);
        assertThat(ItemSearchCondition.page("2\n")).isEqualTo(2);
        assertThat(ItemSearchCondition.page("0999")).isEqualTo(999);
        assertThat(ItemSearchCondition.page("1000")).isEqualTo(999);
        assertThat(ItemSearchCondition.page("99999999999999999999")).isEqualTo(999);
        assertThat(parse("page=3").offset()).isEqualTo(40);
    }

    @Test
    @DisplayName("정렬: 대소문자 무시, 알 수 없는 값은 기본, 방향이 없으면 level · created 만 내림차순")
    void resolvesSort() {
        assertThat(parse("sort=DROP&dir=UP").sort()).isEqualTo(ItemSearchSort.DEFAULT);
        assertThat(parse("sort=unit").sort()).isEqualTo(ItemSearchSort.UNIT_ASC);
        assertThat(parse("sort=Title&dir=DESC").sort()).isEqualTo(ItemSearchSort.TITLE_DESC);
        assertThat(parse("sort=level").sort()).isEqualTo(ItemSearchSort.LEVEL_DESC);
        assertThat(parse("sort=level&dir=asc").sort()).isEqualTo(ItemSearchSort.LEVEL_ASC);
        assertThat(parse("sort=created&dir=x").sort()).isEqualTo(ItemSearchSort.CREATED_DESC);
        assertThat(parse("sort=id&dir=desc").sort()).isEqualTo(ItemSearchSort.ID_DESC);
    }
}
