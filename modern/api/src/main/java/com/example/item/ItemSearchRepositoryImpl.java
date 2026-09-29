package com.example.item;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 문항 검색 JPQL 조립. 레거시는 뷰 {@code v_item_public} 을 조회했지만(legacy/item-bank-php/search.php:522),
 * 여기서는 같은 조건(공개 상태 + 단원 inner join)을 엔티티에 직접 건다. 테스트용 H2 에는 뷰가 없기 때문이다.
 * JPQL 에는 고정 조각만 이어 붙이고, 입력값은 모두 파라미터로 바인딩한다.
 */
class ItemSearchRepositoryImpl implements ItemSearchRepository {

    private static final String FROM = " from Item i join i.unit u";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long countPublicItems(ItemSearchCondition condition) {
        Where where = Where.of(condition);
        TypedQuery<Long> query = entityManager.createQuery("select count(i)" + FROM + where.jpql(), Long.class);
        where.bind(query);
        return query.getSingleResult();
    }

    @Override
    public List<ItemSearchRow> findPublicItems(ItemSearchCondition condition, int offset, int limit) {
        Where where = Where.of(condition);
        TypedQuery<ItemSearchRow> query = entityManager.createQuery(
            "select new com.example.item.ItemSearchRow(i.id, i.title, u.code, i.level)"
                + FROM + where.jpql() + " order by " + condition.sort().jpql(),
            ItemSearchRow.class);
        where.bind(query);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public Map<Integer, List<String>> findTagNamesByItemIds(Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        // 레거시 태그 열: GROUP_CONCAT(t.name ORDER BY t.id)(db/mariadb/init/01-schema.sql:64)
        List<Tuple> tuples = entityManager.createQuery("""
                select i.id as itemId, t.name as tagName
                from Item i join i.tags t
                where i.id in :itemIds
                order by i.id asc, t.id asc
                """, Tuple.class)
            .setParameter("itemIds", itemIds)
            .getResultList();
        return tuples.stream().collect(Collectors.groupingBy(
            tuple -> tuple.get("itemId", Integer.class),
            LinkedHashMap::new,
            Collectors.mapping(tuple -> tuple.get("tagName", String.class), Collectors.toList())));
    }

    /** where 절과 바인딩 값. 조건은 모두 AND 로 묶는다(search.php:44, BR-02). */
    private record Where(String jpql, Map<String, Object> params) {

        static Where of(ItemSearchCondition condition) {
            StringBuilder jpql = new StringBuilder(" where i.status = :status");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("status", ItemStatus.ACTIVE);

            // 키워드: 제목 OR 지문 부분 일치. % · _ 는 이스케이프하지 않는다(search.php:94-98)
            if (condition.keyword() != null) {
                jpql.append(" and (i.title like :keyword or i.stem like :keyword)");
                params.put("keyword", "%" + condition.keyword() + "%");
            }
            // 단원 코드: 입력값 그대로 같음 비교. 대소문자 처리는 DB 정렬 규칙을 따른다(search.php:118-120)
            if (condition.unitCode() != null) {
                jpql.append(" and u.code = :unitCode");
                params.put("unitCode", condition.unitCode());
            }
            // 난이도: 비었으면 level < 5, 아니면 같음(search.php:213-229)
            if (condition.level() == null) {
                jpql.append(" and i.level < :levelUpperBound");
                params.put("levelUpperBound", ItemSearchCondition.DEFAULT_LEVEL_UPPER_BOUND);
            } else {
                jpql.append(" and i.level = :level");
                params.put("level", condition.level());
            }
            // 태그: 이름이 정확히 같은 태그가 달린 문항(search.php:244-247)
            if (condition.tag() != null) {
                jpql.append(" and exists (select 1 from Item i2 join i2.tags t"
                    + " where i2.id = i.id and t.name = :tag)");
                params.put("tag", condition.tag());
            }
            return new Where(jpql.toString(), params);
        }

        void bind(Query query) {
            params.forEach(query::setParameter);
        }
    }
}
