# P1-IT-003 fixture contract

- Authority: `docs/관리데이터/tests.json#P1-IT-003` and the matching Phase 1 design anchor.
- Independent oracle: manifest/preview/populated DB/debug gallery 결과를 교차 비교한다.
- Test data may be generated deterministically by the named test; live save data and network resources are forbidden.
- `assets/one-pixel.webp.base64` is a project-owned, byte-level RIFF/VP8L one-pixel WebP fixture (38 decoded bytes; SHA-256 `52dc24c0429ea6ccc5b579a6da8bb79bf41e471fe5108a62009f3c2e195551c0`). It is retained as Base64 so test execution explicitly owns the decoded bytes; provenance is the WebP RIFF/VP8L format specification and its license is `PROJECT_OWNED_FIXTURE`.
- `assets/one-pixel-vp8l-alpha.webp.base64` is the matching project-owned VP8L fixture with the lossless header alpha-used bit set. It verifies the VP8L-specific alpha contract independently of VP8X/ALPH chunks.
- Run output: `build/phase1-fixtures/P1-IT-003/evidence.json`; a descriptor alone never counts as PASS.
