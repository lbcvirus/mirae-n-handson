package com.example.item;

/**
 * 문항 검색의 한 행(태그 제외). JPQL 생성자 표현식으로 바로 만든다({@code ItemSearchRepositoryImpl}).
 */
public record ItemSearchRow(Integer id, String title, String unitCode, Integer level) {
}
