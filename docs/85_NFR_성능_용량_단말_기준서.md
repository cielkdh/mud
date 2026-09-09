# 85. NFR · 성능 · 용량 · 단말 기준서

> 원문은 “체감 즉시/짧은 대기/수초 내”와 같은 정성 목표를 제시하고 정확 ms/MB는 실제 기기 측정 후 확정하도록 한다. 따라서 아래 숫자는 **「설계 보완안: 초기 Release Gate 후보」**이며 P24 실측 Baseline 승인 후 고정한다.

## 1. 적용 대상

- 완전 오프라인 Android 앱, Native Kotlin/Compose/Room
- 현역 용병 평시 1,500~2,200명, portrait 10,000장
- Inventory 1,000+, 100~300년 장기 세이브, 자동 전투/던전/연대기
- 서버/로그인/현실시간 방치 처리 없음

## 2. 기준 단말 Profile

| Profile | 용도 | 정의 |
|---|---|---|
| MIN | 최저지원 Gate | 64-bit Android, RAM 4GB급, 중저가 CPU, 저장공간 여유 4GB 이상. 실제 모델은 P24에서 고정 |
| STD | 일반 사용자 Gate | RAM 6~8GB급, 중급 CPU/GPU. 실제 모델은 P24에서 고정 |
| DEV | 대량 검증 | JVM/개발 PC. 10k~1M seed 배치에 사용 |

## 3. 성능 Gate 후보

| NFR ID | 대상 | MIN | STD | 측정 | 상태 |
|---|---|---:|---:|---|---|
| NFR-PERF-001 | Cold start P95 | ≤ 3.0s | ≤ 2.0s | Macrobenchmark | PROVISIONAL |
| NFR-PERF-002 | Warm start P95 | ≤ 1.5s | ≤ 1.0s | Macrobenchmark | PROVISIONAL |
| NFR-PERF-003 | 전투 1회 instant 결과 P95 | ≤ 500ms | ≤ 250ms | headless benchmark | PROVISIONAL |
| NFR-PERF-004 | 30일 시간 진행 P95 | ≤ 4.0s | ≤ 2.0s | event-boundary scenario | PROVISIONAL |
| NFR-PERF-005 | NPC 2,000 목록 최초 표시 P95 | ≤ 1.5s | ≤ 1.0s | Macrobenchmark | PROVISIONAL |
| NFR-PERF-006 | 100년 연대기 검색 P95 | ≤ 1.0s | ≤ 500ms | indexed query benchmark | PROVISIONAL |
| NFR-PERF-007 | 자동 incremental save UI blocking | 0 main-thread I/O | 0 main-thread I/O | StrictMode/trace | REQUIRED |
| NFR-PERF-008 | 수동 저장 P95 | ≤ 3.0s | ≤ 2.0s | save fixture | PROVISIONAL |
| NFR-PERF-009 | 일반 load P95 | ≤ 4.0s | ≤ 2.5s | load fixture | PROVISIONAL |
| NFR-PERF-010 | 2,000 NPC list jank | ≤ 8% slow frames | ≤ 5% slow frames | Macrobenchmark frame timing | PROVISIONAL |

### 개발 배치 목표

| NFR ID | 배치 | 초기 Gate 후보 | 원문 정성 기준 |
|---|---|---|---|
| NFR-BATCH-001 | 전투 10,000회 | DEV에서 ≤ 30s 우선 목표 | “수초~수십초” |
| NFR-BATCH-002 | 던전 1,000개 생성+validator | DEV에서 ≤ 60s 우선 목표 | “배치 검증 가능 수준” |
| NFR-BATCH-003 | 100년 월드 | Release build headless로 야간/Balance suite 내 완료 | 개발 전용 |
| NFR-BATCH-004 | 300년 월드 | Release Candidate 안정화 suite 내 완료 | Release 후보 필수 |

## 4. 메모리·용량 Gate

| NFR ID | 항목 | 기준 |
|---|---|---|
| NFR-MEM-001 | Domain model Bitmap 보유 | **0건**. PortraitKey만 저장 |
| NFR-MEM-002 | NPC 2,200 일상 플레이 | OOM/지속 증가 없음; 30분 반복 후 안정 plateau 확인 |
| NFR-MEM-003 | 전투 반복 | 1,000회 instant 후 heap 잔존 객체가 반복 횟수에 선형 증가하지 않음 |
| NFR-SIZE-001 | 100년 save | 절대 MB는 실측 후 freeze. 저중요 데이터 압축으로 연수에 선형 무한 증가 금지 |
| NFR-SIZE-002 | 300년 save | 100년 대비 성장률을 측정하고 압축 미작동 여부 Gate. 목표식은 Baseline 승인 후 고정 |
| NFR-SIZE-003 | portrait 10,000 | 앱 시작 시 전체 decode 금지; 화면 request size 기반 decode |
| NFR-SIZE-004 | Export | staging 여유공간 사전 계산, 기존 save 원본 불변 |

## 5. 결정론·정합성 NFR

| ID | 기준 |
|---|---|
| NFR-DET-001 | 동일 engine/content/balance/schema/rng version + 동일 seed + 동일 command sequence → 동일 stateHash |
| NFR-DET-002 | FPS/기기 성능/렌더 유무가 전투 결과를 변경하지 않음 |
| NFR-DET-003 | 1일×30과 30일×1이 동일 interrupt policy에서 상태 동등성 보장 |
| NFR-DATA-001 | command receipt와 권위 mutation이 같은 commit에서 보임 |
| NFR-DATA-002 | SaveGeneration WRITING/손상 세대를 정상 root로 선택하지 않음 |
| NFR-DATA-003 | 마이그레이션/Import 실패가 기존 슬롯을 변경하지 않음 |

## 6. 배터리/Thread NFR

- UI Main에서 대량 simulation/DB/image decode를 수행하지 않는다.
- World Simulation은 단일 전용 dispatcher/actor로 authoritative write 순서를 유지한다.
- Validation seed worker는 debug/검증용이며 live save와 격리한다.
- WorkManager를 게임 시간 진행에 사용하지 않는다.
- 30일 시간 점프/1,000 combat instant/던전 생성 배치에서 CPU가 종료 후 계속 점유되지 않는다.
- 화면/세션 종료 시 coroutine/job/observer를 cancel·drain하고 leak test를 수행한다.

## 7. 접근성·UX NFR

| ID | 기준 |
|---|---|
| NFR-UX-001 | 중요 상태를 색상만으로 전달하지 않음 |
| NFR-UX-002 | TalkBack에서 핵심 버튼/카드/상태가 의미 있는 label과 순서를 가짐 |
| NFR-UX-003 | 큰 글자에서 CTA/수치/핵심 정보가 잘리지 않음 |
| NFR-UX-004 | 위험 행동은 Hold/Confirm 등 오조작 방어 제공 |
| NFR-UX-005 | 긴 목록 복귀 시 위치/필터/정렬 상태 복원 |
| NFR-UX-006 | 앱을 오래 닫아도 현실시간 때문에 세계 상태가 변경되지 않음 |

## 8. Release Gate 운영

1. P24에서 실제 MIN/STD 단말 모델과 OS/API level을 고정한다.
2. 후보 수치를 3회 이상 반복 측정하여 median/P95와 분산을 기록한다.
3. Baseline Profile/R8 적용 전후에 기능 stateHash 동치를 먼저 확인한다.
4. 기준 미달은 “느림”으로만 기록하지 말고 CPU/DB/GC/image/Compose 구간으로 원인을 분류한다.
5. 기준 변경 시 94 결정대장에 이유·기기·build hash를 남긴다.

## 9. 기존 Phase/Test Trace

| NFR 영역 | 구현/검증 Phase | 대표 기존 Test | 추가 증거 |
|---|---|---|---|
| 시작·목록·DB·이미지 성능 | P24 | `P24-UT-001`, `P24-PT-001` | Macrobenchmark 결과 JSON, trace, build hash |
| 누수·취소·배터리 | P24 | `P24-UT-002`, `P24-FT-002`, `P24-REC-001` | heap dump/Leak report/CPU trace |
| 저장공간·압축·격리 | P24/P3 | `P24-UT-003`, `P24-FT-003`, `P3-REC-001` | save size curve, recovery evidence |
| 최적화 동치/R8/Profile | P24 | `P24-UT-004`, `P24-IT-004` | before/after stateHash + benchmark |
| Release E2E | P25 | `P25-IT-001`, `P25-PT-001`, `P25-OP-001` | signed RC manifest, device matrix |
| Upgrade/Migration | P25/P3 | `P25-IT-002`, `P25-REC-001` | schema fixture, before/after hash |

## 10. 측정 프로토콜

1. 같은 signed build, 같은 content/balance/schema version으로 측정한다.
2. Cold/Warm start는 다른 지표로 기록하고 캐시 상태를 섞지 않는다.
3. 최소 5회 warm-up 후 20회 이상 측정하고 P50/P95를 기록한다. 장기 배치는 seed 수와 worker 수를 함께 기록한다.
4. 전투/시간진행 benchmark는 UI render를 포함한 시나리오와 headless core 시나리오를 구분한다.
5. DB query는 row 수, index, query plan, 반환 row 수를 증거에 포함한다.
6. 이미지 테스트는 decode size/crop profile/cache hit 여부를 기록한다.
7. 성능 최적화 전후에는 동일 seed의 stateHash를 먼저 비교하여 기능 변화와 최적화를 분리한다.

## 11. NFR 실패 분류

| 코드 | 의미 | Release 처리 |
|---|---|---|
| `NFR_BLOCKER` | OOM, save 손상, 결정론 깨짐, main-thread DB 등 핵심 위반 | RC 차단 |
| `NFR_MAJOR` | P95 목표 초과, 장기 데이터 비정상 증가, 높은 jank | 원인/예외 승인 없이는 RC 차단 |
| `NFR_MINOR` | 특정 비핵심 화면의 경미한 목표 초과 | 일정/영향을 기록해 제한 승인 가능 |
| `NFR_MEASURE_REQUIRED` | 실제 단말/asset 미제공으로 기준 미확정 | 해당 Gate 전에 측정·freeze 필수 |

## 12. NFR Definition of Done

- [ ] MIN/STD 실제 단말이 고정되어 있다.
- [ ] PROVISIONAL 목표가 측정 결과와 리뷰를 거쳐 BASELINE 또는 승인된 예외로 바뀌었다.
- [ ] P24/P25 대표 Test에 benchmark artifact가 연결되어 있다.
- [ ] 100년/300년 Save 크기 곡선과 메모리 곡선이 보관되어 있다.
- [ ] R8/Baseline Profile 적용 후 stateHash 회귀가 없다.
- [ ] 오프라인 설치 후 네트워크 없이 전체 핵심 흐름이 동작한다.

