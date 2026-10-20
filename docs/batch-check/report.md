# 배치 점검 리포트

**요약: 점검 기간 2026-09-08 ~ 2026-09-17(10일), 이상 7건(4일 · 4개 job)**

- 대상: `pipeline-samples/batch-logs/*.log`(10개), `pipeline-samples/datamart-counts.csv`(49행)
- 점검 기준: ① 실행 여부(누락 · 종료 기록 없음, `day_open planned=` 대조) ② 중복 실행(같은 날 같은 job 의 SUCCESS 2건 이상, run_id 별) ③ 적재 건수(직전 7일 평균 대비 ±30% 초과 또는 0건) ④ 지연(소요 시간이 그 job 의 다른 날 elapsed 중앙값의 2배 초과)
- 계산 방식: 모든 수치는 `python3` 로 계산했다. build_report_cache 는 `elapsed=` 가 없어 START · SUCCESS 시각 차로 구했다. ③ 의 직전 7일은 CSV 의 같은 job 직전 행(최대 7개)이고, 2026-09-08 은 이전 데이터가 없어 ③ 판정에서 뺐다. 원인은 로그에 적힌 것만 "로그 기록"으로 인용했고, 나머지는 "확인 필요"로 두었다.

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 | 영향 추정 | 다음 확인 사항 |
|---|---|---|---|---|---|
| 2026-09-09 | build_item_stats | ① 실행 여부: 종료 기록 없음 | `2026-09-09.log:2` START(`run_id=bis-20260910-0030-5192`), 마지막 기록은 `2026-09-09.log:4` RUNNING 25%. 그 뒤 SUCCESS · 실패 줄이 없고, `2026-09-09.log:1` planned 목록에는 들어 있다. 다른 9일은 모두 SUCCESS 가 있다. CSV 에도 이날 이 job 행이 없다(09-09 행은 `datamart-counts.csv:7-10` 4개뿐) | `dm.item_stats_daily` 의 09-09 분 적재 기록이 없다. 이 테이블을 읽는 하위 job 기록은 로그에 없다 | 확인 필요: 00:31 이후 배치 러너 프로세스 · 시스템 로그, `dm.item_stats_daily` 09-09 실제 건수, `build_item_stats.lock` 해제 여부. 이후 날짜 로그에 09-09 재실행 기록은 없다 |
| 2026-09-12 | export_grades | ④ 지연 | `2026-09-12.log:16` elapsed=963s. 다른 9일 중앙값 318s 의 3.03배. 25% 단위 진행 간격이 약 4분(`2026-09-12.log:12-14`) | 완료가 평소보다 약 11분 늦었다(`datamart-counts.csv:22` loaded_at 01:16:04). 건수 8257 은 정상 범위 | 확인 필요: 그 시간대 `grade-db` 의 `grade_summary` 조회 부하 · 응답 지연 |
| 2026-09-15 | extract_submissions | ④ 지연 | `2026-09-15.log:20` elapsed=1668s. 다른 9일 중앙값 658s 의 2.53배 | 01:57:50 에 끝나 02:00 시작인 load_datamart 직전까지 이어졌다 | 로그 기록: `2026-09-15.log:19` "source read slow: grade-db … replica lag 412s". 이것이 지연 전체를 설명하는지는 확인 필요: replica lag 추이 |
| 2026-09-15 | load_datamart | ④ 지연 | `2026-09-15.log:40`(`run_id=ldm-20260916-0200-edf5`) elapsed=4357s. 다른 9일 중앙값 901s 의 4.84배. 로그 기록: `2026-09-15.log:27` "elapsed 40m exceeds p95 17m", `2026-09-15.log:28` SLA 02:45 NOT_FINISHED | SLA 알림 뒤 수동 재실행이 일어났고(`2026-09-15.log:30`), 아래 중복 실행으로 이어졌다 | 진행률은 계속 올랐다(`2026-09-15.log:24` 25% → `:37` 90%). 느려진 원인은 확인 필요: 대상 테이블 · DB 부하 |
| 2026-09-15 | load_datamart | ② 중복 실행 | SUCCESS 2건: `2026-09-15.log:40`(edf5), `2026-09-15.log:42`(`run_id=ldm-20260916-0258-8963`), 둘 다 rows=184532. `2026-09-15.log:30` 수동 재실행 `force=true`, `2026-09-15.log:31` 잠금을 edf5 가 쥐고 있었으나 `action=ignored`, `2026-09-15.log:32` `lock=bypassed`. 두 실행 모두 `mode=append`(`2026-09-15.log:22`, `:33`). `2026-09-15.log:43` `lock=release_skipped` | `dm.daily_submission_fact` 의 09-15 분이 두 번 적재되었다(아래 건수 행과 일치) | 확인 필요: 중복분 정리 여부, `--force` 재실행이 잠금을 무시한 경위와 운영 절차 |
| 2026-09-15 | load_datamart | ③ 적재 건수 | `datamart-counts.csv:39` 369064. 직전 7일(09-08 ~ 09-14) 평균 184617.4 대비 +99.9%. 두 실행의 rows 합(184532 × 2)과 같다. 로그 기록: `2026-09-15.log:47` dq-check deviation=+99% | 같은 날 build_report_cache 가 이 테이블을 입력으로 읽었다(`2026-09-15.log:45`, `input_rows=369064` 경고) | 확인 필요: build_report_cache 09-15 결과(`datamart-counts.csv:40`, 1271)가 맞는지, 중복 정리 후 재생성 필요 여부 |
| 2026-09-17 | build_item_stats | ③ 적재 건수 | `datamart-counts.csv:46` 7284. 직전 7일 평균 12453.1 대비 −41.5%. `2026-09-17.log:8` SUCCESS rows=7284, elapsed=202s(평소 수준) | `dm.item_stats_daily` 의 09-17 분이 평소보다 약 4할 적다. 이 테이블을 읽는 하위 job 기록은 로그에 없다 | 확인 필요: 원천 `itembank.item_response` 의 09-17 건수(`2026-09-17.log:3`), 그날 응답 데이터가 실제로 적었는지 |

### 참고: 기준에는 해당하지 않은 관찰

- 매일 load_datamart validate 경고(`skipped=1`, `class_id is NULL`)가 같은 학생 1명에게 반복된다(10일 · 11회, 예: `2026-09-08.log:22`). 원천 데이터 확인이 필요하다.
- 09-15 의 부풀려진 건수가 이후 날짜의 직전 7일 평균에 들어간다. 예를 들어 09-17 load_datamart(`datamart-counts.csv:49`, 184551)의 직전 7일 평균은 211493.4 로 올라가 편차가 −12.7% 로 계산된다. 중복분을 정리하면 평균을 다시 계산해야 한다.
- 로그 원문에 학생 식별자와 운영자 · 알림 이메일이 들어 있어 이 리포트에는 옮기지 않았다.

## 이상 없음으로 판정한 날짜

- 2026-09-08 (③ 은 비교할 이전 데이터가 없어 판정 제외)
- 2026-09-10
- 2026-09-11
- 2026-09-13
- 2026-09-14
- 2026-09-16
