# P1-UT-003 fixture contract

- Authority: `docs/관리데이터/tests.json#P1-UT-003` and the matching Phase 1 design anchor.
- Independent oracle: ContentRepository 6 read와 exact AssetResolver 결과를 oracle과 비교한다.
- Test data may be generated deterministically by the named test; live save data and network resources are forbidden.
- Run output: `build/phase1-fixtures/P1-UT-003/evidence.json`; a descriptor alone never counts as PASS.
