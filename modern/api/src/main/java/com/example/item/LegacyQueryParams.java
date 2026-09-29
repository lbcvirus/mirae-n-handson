package com.example.item;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 쿼리스트링 원문을 PHP {@code $_GET} 과 같은 규칙으로 읽는다(문항 검색 이관용).
 * <ul>
 *   <li>{@code level[]=5&level[]=1} 은 배열이고 첫 값 5 를 쓴다 — legacy/item-bank-php/search.php:51-80</li>
 *   <li>{@code level=3&level=4} 처럼 같은 이름이 반복되면 뒤의 값 4 가 앞의 값을 덮는다(PHP 규칙)</li>
 *   <li>{@code +} 는 공백, 잘못된 {@code %} 인코딩은 오류 없이 글자 그대로 둔다(PHP {@code urldecode} 와 같음)</li>
 * </ul>
 * Spring 의 {@code @RequestParam String} 은 반복 값을 쉼표로 이어 붙이고 {@code level[]} 을 {@code level} 로 보지
 * 않아서 쓰지 않는다.
 */
final class LegacyQueryParams {

    private static final int HEX_RADIX = 16;
    private static final int PERCENT_ESCAPE_LENGTH = 3;

    private final Map<String, String> values;

    private LegacyQueryParams(Map<String, String> values) {
        this.values = values;
    }

    static LegacyQueryParams parse(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        Set<String> arrayNames = new HashSet<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return new LegacyQueryParams(values);
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = stripLeadingSpaces(decode(eq < 0 ? pair : pair.substring(0, eq)));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            int open = name.indexOf('[');
            boolean isArray = open > 0 && name.indexOf(']', open) > open;
            if (!isArray) {
                values.put(name, value);
                arrayNames.remove(name);
            } else {
                String base = name.substring(0, open);
                // 배열은 첫 원소만 쓴다. 앞에 일반 값이 있었으면 배열이 그 값을 덮는다
                if (!arrayNames.contains(base)) {
                    values.put(base, value);
                    arrayNames.add(base);
                }
            }
        }
        return new LegacyQueryParams(values);
    }

    /** 파라미터 값. 없으면 {@code defaultValue}. */
    String get(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    /** PHP 는 변수 이름 앞의 공백을 무시한다. */
    private static String stripLeadingSpaces(String name) {
        int i = 0;
        while (i < name.length() && name.charAt(i) == ' ') {
            i++;
        }
        return name.substring(i);
    }

    private static String decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '+') {
                out.write(' ');
                i++;
                continue;
            }
            if (c == '%' && i + PERCENT_ESCAPE_LENGTH <= s.length()) {
                int high = Character.digit(s.charAt(i + 1), HEX_RADIX);
                int low = Character.digit(s.charAt(i + PERCENT_ESCAPE_LENGTH - 1), HEX_RADIX);
                if (high >= 0 && low >= 0) {
                    out.write(high * HEX_RADIX + low);
                    i += PERCENT_ESCAPE_LENGTH;
                    continue;
                }
            }
            int codePoint = s.codePointAt(i);
            out.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
            i += Character.charCount(codePoint);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
