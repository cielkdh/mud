# QA 인수인계

- 역할과 책임: Phase와 Release 범위의 전체 제품 품질을 독립 판정한다. Acceptance Criteria, 실제 사용자 흐름, UI/UX·접근성, Unit/Integration/UI/Regression/Runtime, 경계·실패·복구·Save/Load/Migration, 성능·메모리·오프라인·Emulator/Device를 포함한다.
- 개발자 테스트·계약·직접 실행 결과는 QA 참고 자료이며 QA 판정을 대체하지 않는다. QA는 최종 코드와 제품 흐름을 직접 확인·실행한다. Phase QA와 Release QA는 동일 QA 책임의 범위 차이로 기록한다.
- 기본 확인: cold/warm launch, 정상·중단·취소·오류·복구 흐름, lifecycle/process death, 접근성, 긴 문구·큰 글자·좁은 화면, 성능 체감, 사용자에게 보이는 손실·다음 행동의 명확성을 확인한다.
- 현재 담당 영역: Phase 1 최종 Gate와 Android API 36 runtime 직접 검증.
- 최근 상태: 고정 QA Thread가 반복해 빈 최종 응답으로 종료되어 대체 독립 QA를 수행했다. 정상·경계·실패·복구·회귀 흐름을 직접 확인하고 `APPROVED WITH CONDITIONS`로 판정했다.
- 미해결 이슈: P0/P1 없음. screenshot 시각 품질, Test별 실행환경 문구, 실기기·TalkBack·실물 자산은 후속 Gate다.
- 다음 작업: P22~P25에서 production UI, MIN/STD 실기기, 실제 10,000 자산과 `FULL_CONTENT_READY`를 별도 검증한다.
- 주의사항: Windows logcat 파일 잠금 충돌을 피하기 위해 공유 checkout에서 Gradle/ADB writer를 병렬 실행하지 않는다.
- 현재 환경 우선 Thread: `codex://threads/01a08f84-2809-7c21-9651-dadac06b9c93` — Phase 1에서 최종 Chat 본문이 비어 대체 독립 QA 사용
- 이전 Thread 참조: `codex://threads/01a098a8-e65f-7502-8563-23225dfe21fc`
