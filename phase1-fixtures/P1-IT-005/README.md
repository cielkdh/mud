# P1-IT-005 fixture contract

- Authority: `docs/관리데이터/tests.json#P1-IT-005` and the matching Phase 1 design anchor.
- Independent oracle: source→DB/preview→runtime resolver→compatibility handoff 전체 artifact를 집계 검증한다.
- Test data may be generated deterministically by the named test; live save data and network resources are forbidden.
- Run output: `build/phase1-fixtures/P1-IT-005/evidence.json`; a descriptor alone never counts as PASS.
