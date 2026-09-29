package com.example.item;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 문항 검색용 리포지토리 조각. 조건 조합에 따라 JPQL 이 달라져서 {@code ItemSearchRepositoryImpl} 이 직접 조립한다.
 * 대상은 공개 문항({@link ItemStatus#ACTIVE})뿐이다.
 */
public interface ItemSearchRepository {

    /** 조건에 맞는 공개 문항 수. */
    long countPublicItems(ItemSearchCondition condition);

    /** 조건에 맞는 공개 문항 중 {@code offset} 부터 {@code limit} 건. 정렬은 {@code condition.sort()}. */
    List<ItemSearchRow> findPublicItems(ItemSearchCondition condition, int offset, int limit);

    /** 문항 id → 태그 이름 목록(태그 id 순). 태그가 없는 문항은 키가 없다. */
    Map<Integer, List<String>> findTagNamesByItemIds(Collection<Integer> itemIds);
}
