package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class LegacyQueryParamsTest {

    @Test
    @DisplayName("PHP 배열 표기(level[]) 는 level 로 보고 첫 값을 쓴다")
    void usesFirstValueOfArrayParam() {
        LegacyQueryParams params = LegacyQueryParams.parse("level%5B%5D=5&level%5B%5D=1&tag[]=%EA%B3%84%EC%82%B0");

        assertThat(params.get("level", "")).isEqualTo("5");
        assertThat(params.get("tag", "")).isEqualTo("계산");
    }

    @Test
    @DisplayName("같은 이름이 반복되면 뒤의 값이 앞의 값을 덮는다")
    void laterPlainValueOverridesEarlierOne() {
        assertThat(LegacyQueryParams.parse("level=3&level=4").get("level", "")).isEqualTo("4");
        assertThat(LegacyQueryParams.parse("level%5B%5D=5&level=3").get("level", "")).isEqualTo("3");
        assertThat(LegacyQueryParams.parse("level=3&level%5B%5D=5").get("level", "")).isEqualTo("5");
    }

    @Test
    @DisplayName("+ 는 공백, 잘못된 % 인코딩은 그대로, = 가 없으면 빈 값, 없는 이름은 기본값")
    void decodesLeniently() {
        LegacyQueryParams params = LegacyQueryParams.parse("q=a+b%zz%&unit&%20sort=id");

        assertThat(params.get("q", "")).isEqualTo("a b%zz%");
        assertThat(params.get("unit", "x")).isEmpty();
        assertThat(params.get("sort", "")).isEqualTo("id");
        assertThat(params.get("page", "1")).isEqualTo("1");
        assertThat(LegacyQueryParams.parse(null).get("q", "")).isEmpty();
    }
}
