package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과의 한 행. 필드 이름은 동작 보존 테스트의 정규화 결과(characterization/lib/normalize.mjs)와 같다.
 * {@code unit} 은 단원 코드, {@code tags} 는 태그 이름 목록(태그 id 순, 없으면 빈 배열).
 */
public record ItemSearchRowResponse(Integer id, String title, String unit, Integer level, List<String> tags) {

    static ItemSearchRowResponse of(ItemSearchRow row, List<String> tags) {
        return new ItemSearchRowResponse(row.id(), row.title(), row.unitCode(), row.level(), tags);
    }
}
