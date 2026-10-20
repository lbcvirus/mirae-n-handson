# 배치 점검 리포트

**요약: 점검 기간 2026-09-08 ~ 2026-09-17(10일), 이상 7건(4일 · 4개 job)**

- 대상: `pipeline-samples/batch-logs/*.log`, `pipeline-samples/datamart-counts.csv`
- 점검 기준: ① 실행 여부(누락 · 종료 기록 없음) ② 중복 실행(같은 날 SUCCESS 2건 이상) ③ 적재 건수(직전 7일 평균 대비 ±30% 초과 또는 0건) ④ 지연(소요 시간이 평소의 2배 초과)
- 계산 방식: "평소 소요 시간"은 그 job 의 다른 날 elapsed 중앙값이다. build_report_cache 는 elapsed 필드가 없어 START · SUCCESS 시각 차로 계산했다. 직전 7일 평균은 CSV 의 직전 행으로 계산했고, 2026-09-08 은 비교할 이전 데이터가 없어 ③ 판정에서 뺐다.
- 원인은 로그에 기록된 것만 적었다. 로그에 없는 원인은 "확인 필요"로 두었다.

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 | 영향 추정 | 다음 확인 사항 |
|---|---|---|---|---|---|
| 2026-09-09 | build_item_stats | ① 실행 여부: 종료 기록 없음 | `2026-09-09.log:2` START(`run_id=bis-20260910-0030-5192`). 마지막 기록은 `2026-09-09.log:4` RUNNING 25%이고, 그 뒤 SUCCESS · 실패 줄이 없다. 다른 9일은 모두 `:8` 에 SUCCESS 가 있다. `datamart-counts.csv` 에도 이날 이 job 행이 없다(09-09 행 4개) | `dm.item_stats_daily` 의 09-09 분이 빠졌거나 일부만 들어갔을 수 있다 | 원인 확인 필요: 00:31 이후 배치 러너 프로세스 · 시스템 로그, `dm.item_stats_daily` 의 09-09 실제 건수, 잠금 파일(`build_item_stats.lock`) 해제 여부 |
| 2026-09-12 | export_grades | ④ 지연 | `2026-09-12.log:16` elapsed=963s. 다른 9일 중앙값 318s 의 3.03배. 25% 단위 진행 간격이 약 4분(`2026-09-12.log:12-15`) | 산출물 생성이 약 11분 늦었다. 건수(`datamart-counts.csv:22`, 8257)는 정상 범위 | 원인 확인 필요: 그 시간대 `grade-db` 의 `grade_summary` 조회 부하 · 응답 지연 지표 |
| 2026-09-15 | extract_submissions | ④ 지연 | `2026-09-15.log:20` elapsed=1668s. 다른 9일 중앙값 658s 의 2.53배 | 뒤이은 load_datamart 의 시작 시점에 이 job 이 끝나 있었는지는 로그로 확인되지 않는다 | 로그에 기록된 정황: `2026-09-15.log:19` "source read slow: grade-db … replica lag 412s". 이것이 지연 전체의 원인인지 확인 필요: replica lag 추이 |
| 2026-09-15 | load_datamart | ④ 지연 | `2026-09-15.log:40`(`run_id=ldm-20260916-0200-edf5`) elapsed=4357s. 다른 9일 중앙값 901s 의 4.84배. `2026-09-15.log:27` 에 "elapsed 40m exceeds p95 17m" 경고, `2026-09-15.log:28` 에 SLA 02:45 기준 NOT_FINISHED | SLA 를 넘겨 수동 재실행이 일어났고, 이것이 아래 중복 실행으로 이어졌다 | 진행률은 계속 올랐다(edf5 기준 `2026-09-15.log:24` 25% → `:37` 90%). 느려진 원인 확인 필요: 대상 테이블 · DB 부하, 선행 job(extract_submissions) 지연과의 관계 |
| 2026-09-15 | load_datamart | ② 중복 실행 | SUCCESS 2건: `2026-09-15.log:40`(edf5), `2026-09-15.log:42`(`run_id=ldm-20260916-0258-8963`), 둘 다 rows=184532. `2026-09-15.log:30` 수동 재실행 `force=true`. `2026-09-15.log:31` 잠금을 edf5 가 쥐고 있었으나 `action=ignored`. 두 실행 모두 `mode=append`(`2026-09-15.log:22`, `:33`). `2026-09-15.log:43` `lock=release_skipped` | `dm.daily_submission_fact` 의 09-15 분이 두 번 적재되었을 가능성이 크다(아래 건수 행과 일치) | 중복분 정리 여부 확인 필요. `--force` 재실행이 잠금을 무시한 경위와 운영 절차 확인 필요 |
| 2026-09-15 | load_datamart | ③ 적재 건수 | `datamart-counts.csv:39` 369064. 직전 7일(09-08 ~ 09-14) 평균 184617.4 대비 +99.9%. 184532 × 2 = 369064 로 두 실행의 rows 합과 같다. `2026-09-15.log:47` dq-check 도 deviation=+99% 경고 | 같은 날 build_report_cache 가 이 테이블을 입력으로 읽었다(`2026-09-15.log:45`, `input_rows=369064` 경고). 리포트 캐시와 그 하위 리포트 값이 영향을 받았을 수 있다 | build_report_cache 09-15 결과(`datamart-counts.csv:40`, 1271)가 맞는지 확인 필요. 중복 정리 후 재생성 필요 여부 확인 필요 |
| 2026-09-17 | build_item_stats | ③ 적재 건수 | `datamart-counts.csv:46` 7284. 직전 7일 평균 12453.1 대비 −41.5%. `2026-09-17.log:8` SUCCESS rows=7284, elapsed=202s(평소 수준) | `dm.item_stats_daily` 의 09-17 문항 통계가 평소보다 약 4할 적다 | 원인 확인 필요: 원천 `itembank.item_response` 의 09-17 건수, 그날 응답 데이터가 실제로 적었는지 |

### 참고: 기준에는 해당하지 않은 관찰

- 모든 날에 load_datamart validate 경고(`skipped=1`, `class_id is NULL`)가 같은 학생 1명에게 반복된다(예: `2026-09-08.log:22`). 원천 데이터 확인이 필요하다.
- 로그 원문에 학생 식별자와 운영자 · 알림 이메일이 들어 있어, 이 리포트에는 옮기지 않았다.

## 이상 없음으로 판정한 날짜

- 2026-09-08 (③ 은 비교할 이전 데이터가 없어 판정 제외)
- 2026-09-10
- 2026-09-11
- 2026-09-13
- 2026-09-14
- 2026-09-16
