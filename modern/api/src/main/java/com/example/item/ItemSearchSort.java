package com.example.item;

/**
 * 문항 검색 정렬. 각 상수는 JPQL {@code order by} 조각(별칭 {@code i}=Item, {@code u}=Unit)을 가진다.
 * 레거시 근거: legacy/item-bank-php/search.php:266-327
 */
public enum ItemSearchSort {

    ID_ASC("i.id asc"),
    ID_DESC("i.id desc"),
    TITLE_ASC("i.title asc, i.id asc"),
    TITLE_DESC("i.title desc, i.id asc"),
    /** 방향과 관계없이 2차 정렬은 늘 난이도 내림차순이다(search.php:293-299). */
    UNIT_ASC("u.code asc, i.level desc, i.id asc"),
    UNIT_DESC("u.code desc, i.level desc, i.id asc"),
    LEVEL_ASC("i.level asc, i.id asc"),
    LEVEL_DESC("i.level desc, i.id asc"),
    CREATED_ASC("i.createdAt asc, i.id asc"),
    CREATED_DESC("i.createdAt desc, i.id asc"),
    /** 비었거나 알 수 없는 정렬 기준: 어려운 문항부터, 같은 난이도면 번호 순(search.php:317-326). */
    DEFAULT("i.level desc, i.id asc");

    private final String jpql;

    ItemSearchSort(String jpql) {
        this.jpql = jpql;
    }

    String jpql() {
        return jpql;
    }

    /**
     * trim · ASCII 소문자로 바꾼 {@code sort} · {@code dir} → 정렬(search.php:266-267).
     * {@code dir} 가 asc · desc 가 아니면 무시하고, 그때 기본 방향은 {@code level} · {@code created} 만 내림차순이다
     * (search.php:268-273, 278-315).
     */
    static ItemSearchSort resolve(String sort, String dir) {
        boolean asc = "asc".equals(dir);
        boolean desc = "desc".equals(dir);
        return switch (sort) {
            case "id" -> desc ? ID_DESC : ID_ASC;
            case "title" -> desc ? TITLE_DESC : TITLE_ASC;
            case "unit" -> desc ? UNIT_DESC : UNIT_ASC;
            case "level" -> asc ? LEVEL_ASC : LEVEL_DESC;
            case "created" -> asc ? CREATED_ASC : CREATED_DESC;
            default -> DEFAULT;
        };
    }
}
