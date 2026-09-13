# P1-IT-002 fixture contract

- Authority: `docs/관리데이터/tests.json#P1-IT-002` and the matching Phase 1 design anchor.
- Independent oracle: populated SQLite의 prepared roundtrip, sealing, idempotency, integrity를 검사한다.
- Test data may be generated deterministically by the named test; live save data and network resources are forbidden.
- Run output: `build/phase1-fixtures/P1-IT-002/evidence.json`; a descriptor alone never counts as PASS.
