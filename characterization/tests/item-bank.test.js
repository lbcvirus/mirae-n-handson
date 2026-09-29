// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 케이스 설계 근거: docs/item-bank/BUSINESS-RULES.md 의 BR-01 ~ BR-21.
// 케이스 이름 앞의 [정상] · [경계] · [빈값] · [이상값] 은 README 의 케이스 작성 규칙 1 의 분류다.
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 레거시 기본 주소).
// - 경로는 레거시 경로(/search.php)로 적는다. 새 API 가 404 를 주면 PATH_ALIASES 의 새 경로로 다시 요청한다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  describe('정상 입력', () => {
    it('[정상] 단원 M5-1 + 난이도 3 — 조건 AND 결합, 검수중(R) 문항 제외 (BR-01 · 02 · 06 · 09)', async () => {
      expect(await search({ unit: 'M5-1', level: '3' })).toMatchSnapshot();
    });

    it('[정상] 키워드 분수 + 태그 계산 + 제목 오름차순 — 동점은 id 오름차순 (BR-02 · 03 · 11 · 15)', async () => {
      expect(await search({ q: '분수', tag: '계산', sort: 'title', dir: 'asc' })).toMatchSnapshot();
    });

    it('[정상] 난이도 5 + 단원 정렬, 방향 없음 — 2차 정렬 level DESC, 검수중 30번 제외 (BR-01 · 09 · 15 · 16)', async () => {
      expect(await search({ level: '5', sort: 'unit' })).toMatchSnapshot();
    });
  });

  describe('경계값', () => {
    it('[경계] 키워드 정확히 100자 — 자르지 않는 상한 (BR-03 · 04)', async () => {
      expect(await search({ q: '가'.repeat(100) })).toMatchSnapshot();
    });

    it('[경계] 키워드 101자 — 앞 100자만 사용, 101번째 글자 버림 (BR-04)', async () => {
      expect(await search({ q: '가'.repeat(100) + '나' })).toMatchSnapshot();
    });

    it('[경계] 키워드 한 글자 — 경고만 하고 부분 일치 검색 (BR-03 · 05)', async () => {
      expect(await search({ q: '분' })).toMatchSnapshot();
    });

    it('[경계] 페이지 999 — 보정하지 않는 최대 페이지 (BR-19 · 20)', async () => {
      expect(await search({ page: '999' })).toMatchSnapshot();
    });

    it('[경계] 페이지 1000 — 999 로 보정 (BR-19 · 20)', async () => {
      expect(await search({ page: '1000' })).toMatchSnapshot();
    });

    it('[경계] 전체 20건(한 페이지 크기와 같음)일 때 2페이지 — 마지막 쪽을 넘는 페이지 (BR-08 · 18 · 20 · 21)', async () => {
      expect(await search({ page: '2' })).toMatchSnapshot();
    });

    it('[경계] 태그 51자 — 앞 50자만 사용, 등록되지 않은 태그로 조회 (BR-11 · 12)', async () => {
      expect(await search({ tag: '가'.repeat(51) })).toMatchSnapshot();
    });
  });

  describe('빈 값 · 누락', () => {
    it('[빈값] 파라미터 없음 — 빈 난이도는 level < 5, 기본 정렬, 페이지 링크 없음 (BR-01 · 08 · 14 · 18 · 21)', async () => {
      expect(await search({})).toMatchSnapshot();
    });

    it('[빈값] 모든 파라미터 빈 값, 키워드는 공백만 — 파라미터 없음과 같은 결과인지 (BR-03 · 06 · 08 · 14 · 16 · 19)', async () => {
      expect(
        await search({ q: '  ', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
      ).toMatchSnapshot();
    });
  });

  describe('이상한 값', () => {
    it('[이상값] 음수 페이지 -5 — 1페이지로 보정 (BR-19)', async () => {
      expect(await search({ page: '-5' })).toMatchSnapshot();
    });

    it('[이상값] 페이지 0 — 1페이지로 보정 (BR-19)', async () => {
      expect(await search({ page: '0' })).toMatchSnapshot();
    });

    it('[이상값] 형식은 맞지만 없는 단원 코드 Z9-99 — 경고만 하고 조회 (BR-06)', async () => {
      expect(await search({ unit: 'Z9-99' })).toMatchSnapshot();
    });

    it('[이상값] 형식이 틀린 단원 코드 ABC — 경고만 하고 조회 (BR-06 · 07)', async () => {
      expect(await search({ unit: 'ABC' })).toMatchSnapshot();
    });

    it('[이상값] 소문자 단원 코드 m5-1 — 대소문자 구분 여부 미확인 (BR-06)', async () => {
      expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
    });

    it('[이상값] 난이도 3abc — 정수 변환으로 3 과 비교되는지 (BR-10)', async () => {
      expect(await search({ level: '3abc' })).toMatchSnapshot();
    });

    it('[이상값] 난이도 0 — 범위 밖 (BR-09 · 10)', async () => {
      expect(await search({ level: '0' })).toMatchSnapshot();
    });

    it('[이상값] 난이도 6 — 범위 밖 (BR-09 · 10)', async () => {
      expect(await search({ level: '6' })).toMatchSnapshot();
    });

    it('[이상값] 아주 긴 키워드 5000자 — 100자로 잘라 오류 없이 조회 (BR-04)', async () => {
      expect(await search({ q: 'a'.repeat(5000) })).toMatchSnapshot();
    });

    it('[이상값] 키워드 % + 알 수 없는 정렬 DROP · 방향 UP — 와일드카드, 기본 정렬로 대체 (BR-03 · 05 · 14 · 16)', async () => {
      expect(await search({ q: '%', sort: 'DROP', dir: 'UP' })).toMatchSnapshot();
    });

    it('[이상값] 같은 파라미터를 배열로 전송 level[]=5,1 · tag[]=계산,개념 — 첫 값만 사용 (BR-11 · 13)', async () => {
      expect(await search({ 'level[]': ['5', '1'], 'tag[]': ['계산', '개념'] })).toMatchSnapshot();
    });
  });
});
