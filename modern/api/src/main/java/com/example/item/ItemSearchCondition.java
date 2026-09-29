package com.example.item;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문항 검색 조건. 레거시 검색 화면(legacy/item-bank-php/search.php)의 입력 보정 규칙을 적용한 결과다.
 * 레거시가 경고만 띄우고 조회는 그대로 하던 입력(없는 단원, 범위 밖 난이도 등)도 오류로 막지 않는다.
 *
 * @param keyword 제목 · 지문 부분 일치 키워드. 없으면 {@code null}
 * @param unitCode 단원 코드. 없으면 {@code null}
 * @param level 같아야 하는 난이도. {@code null} 이면 {@code level < DEFAULT_LEVEL_UPPER_BOUND}
 * @param tag 정확히 일치해야 하는 태그 이름. 없으면 {@code null}
 * @param sort 정렬
 * @param page 1 ~ {@code MAX_PAGE} 로 보정한 페이지 번호
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Integer level,
    String tag,
    ItemSearchSort sort,
    int page) {

    /** 한 페이지 결과 수(search.php:349, 523). */
    public static final int PAGE_SIZE = 20;
    /** 키워드 최대 글자 수(search.php:86-88). */
    static final int MAX_KEYWORD_LENGTH = 100;
    /** 태그 이름 최대 글자 수(search.php:240-242). */
    static final int MAX_TAG_LENGTH = 50;
    /** 최대 페이지 번호(search.php:345-347). */
    static final int MAX_PAGE = 999;
    static final int FIRST_PAGE = 1;
    /**
     * 난이도가 비었을 때 붙는 조건 {@code level < 5} 의 상한(search.php:213-216).
     * 레거시 주석은 "1~5 모두 포함"이지만 코드는 난이도 5 를 뺀다. 코드 동작 그대로 옮긴다.
     */
    public static final int DEFAULT_LEVEL_UPPER_BOUND = 5;

    /** PHP {@code trim()} 기본 제거 문자: 공백, \t, \n, \r, \0, \x0B. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";

    /** PHP {@code (int)"..."} 가 읽는 숫자 앞부분: 앞 공백, 부호, 정수 · 소수, 지수. 뒤의 나머지는 버린다. */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("^[ \\t\\n\\r\\u000B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    /** 페이지 원문 검사 {@code /^[0-9]+$/}. PCRE 의 {@code $} 는 끝 줄바꿈 하나를 허용한다(search.php:337). */
    private static final Pattern PAGE_DIGITS = Pattern.compile("[0-9]+\n?");

    /** 쿼리 파라미터 → 검색 조건. 없는 파라미터는 레거시처럼 빈 값(page 는 "1")으로 본다(search.php:53-80). */
    static ItemSearchCondition from(LegacyQueryParams params) {
        return new ItemSearchCondition(
            keyword(params.get("q", "")),
            unitCode(params.get("unit", "")),
            level(params.get("level", "")),
            tag(params.get("tag", "")),
            ItemSearchSort.resolve(
                toLowerAscii(phpTrim(params.get("sort", ""))),
                toLowerAscii(phpTrim(params.get("dir", "")))),
            page(params.get("page", "1")));
    }

    /** 결과에서 건너뛸 행 수 {@code (page - 1) × 20}(search.php:349). */
    public int offset() {
        return (page - 1) * PAGE_SIZE;
    }

    /** 앞뒤 공백을 빼고 100자까지만 쓴다. 비면 조건 없음(search.php:85-90). */
    static String keyword(String raw) {
        String q = truncate(phpTrim(raw), MAX_KEYWORD_LENGTH);
        return q.isEmpty() ? null : q;
    }

    /** 앞뒤 공백만 뺀다. 형식 · 대소문자 · 존재 여부는 검사하지 않고 그대로 조회한다(search.php:109-120). */
    static String unitCode(String raw) {
        String unit = phpTrim(raw);
        return unit.isEmpty() ? null : unit;
    }

    /**
     * 비었으면 {@code null}(기본 범위). 그 밖에는 PHP {@code (int)} 변환 값과 같은 난이도를 찾는다.
     * {@code 1}~{@code 5} 한 글자(search.php:217-220)도, 범위 밖 값(search.php:223-229)도 같은 변환을 거친다.
     * 그래서 {@code 3abc} 는 3 이 된다.
     */
    static Integer level(String raw) {
        String level = phpTrim(raw);
        return level.isEmpty() ? null : phpIntval(level);
    }

    /** 앞뒤 공백을 빼고 50자까지만 쓴다. 비면 조건 없음(search.php:235-243). */
    static String tag(String raw) {
        String tag = phpTrim(raw);
        return tag.isEmpty() ? null : truncate(tag, MAX_TAG_LENGTH);
    }

    /** 숫자로만 된 원문(trim 하지 않음)이 아니면 1. 1 보다 작으면 1, 999 보다 크면 999(search.php:336-348). */
    static int page(String raw) {
        int page = PAGE_DIGITS.matcher(raw).matches() ? phpIntval(raw) : FIRST_PAGE;
        return Math.min(Math.max(page, FIRST_PAGE), MAX_PAGE);
    }

    /** PHP {@code trim($s)}. Java {@code trim()} 와 달리 \f 등 다른 제어 문자는 남긴다. */
    static String phpTrim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(s.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        return s.substring(start, end);
    }

    /**
     * PHP {@code (int)$s}. 숫자 앞부분만 읽고({@code "3.7"} → 3, {@code "1e3"} → 1000), 숫자로 시작하지 않으면 0 이다.
     * 범위를 넘으면 int 최댓값 · 최솟값으로 고정한다. TINYINT 컬럼과 비교하므로 결과는 PHP 와 같다(0건).
     */
    static int phpIntval(String s) {
        Matcher m = PHP_NUMERIC_PREFIX.matcher(s);
        if (!m.find()) {
            return 0;
        }
        // double → int 변환은 0 쪽으로 잘라 내고, 범위를 넘으면 int 최댓값 · 최솟값으로 고정한다
        return (int) Double.parseDouble(m.group(1));
    }

    /** PHP {@code strtolower($s)}(C 로캘) — ASCII 대문자만 소문자로 바꾼다. */
    private static String toLowerAscii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? Character.toLowerCase(c) : c);
        }
        return sb.toString();
    }

    /** PHP {@code mb_substr($s, 0, max, 'UTF-8')} — 글자 수는 코드포인트 기준(Java {@code length()} 아님). */
    private static String truncate(String s, int maxChars) {
        if (s.codePointCount(0, s.length()) <= maxChars) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxChars));
    }
}
