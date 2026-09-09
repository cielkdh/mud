#!/usr/bin/env python3
"""관리 JSON에서 진행현황 Markdown을 다시 생성한다. Python 3.10+ / 외부 의존성 없음."""
from __future__ import annotations
import json
from collections import Counter
from datetime import datetime, timezone, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVE_FUNCTIONS = {'FUNC-P0-001', 'FUNC-P0-002'}

def load(name: str):
    return json.loads((ROOT / '관리데이터' / f'{name}.json').read_text(encoding='utf-8'))

def main() -> int:
    phases, tasks, tests, decisions = (load(x) for x in ('phases', 'tasks', 'tests', 'decisions'))
    by_task = {x['id']: x for x in tasks}
    by_test = {x['id']: x for x in tests}
    task_counts, test_counts = Counter(x['status'] for x in tasks), Counter(x['status'] for x in tests)
    inconsistencies: list[str] = []
    for task in tasks:
        if task['status'] == 'DONE':
            if any(by_task[d]['status'] != 'DONE' for d in task['depends']):
                inconsistencies.append(f"{task['id']}: 미완료 선행 Task가 있습니다.")
            if not task.get('pr'):
                inconsistencies.append(f"{task['id']}: PR/리뷰 증거가 없습니다.")
            # 계약 Task는 테스트 설계/Fixture 작성 완료이며 실제 테스트 PASS는 검증/Gate에서 요구한다.
            if task['stage'] in ('검증', 'Gate', '데이터검수'):
                for test_id in task['tests']:
                    test = by_test[test_id]
                    if test['status'] != 'PASS' or not test.get('evidence'):
                        inconsistencies.append(f"{task['id']}: {test_id}의 PASS 및 실행 증거가 필요합니다.")
    for test in tests:
        if test['status'] == 'PASS' and not test.get('evidence'):
            inconsistencies.append(f"{test['id']}: 실행 증거 없는 PASS입니다.")
    total_effort = sum(t['expected_days'] for t in tasks)
    completed_effort = sum(t['expected_days'] for t in tasks if t['status'] == 'DONE')
    now = datetime.now(timezone(timedelta(hours=9))).isoformat(timespec='seconds')
    lines = [
        '# 98. 진행현황 대시보드', '', f'> 생성시각: {now} · 데이터 원천: `관리데이터/*.json`', '',
        '이 표는 제출된 코드의 실제 개발 진척을 추정하지 않는다. 초기 관리 기준선은 코드·실행 증거가 없으므로 NOT_STARTED / NOT_RUN이다. 상태를 변경한 뒤 이 스크립트를 실행하면 표가 갱신된다.', '',
        '## 1. 전체 현황', '',
        f"Task: **{len(tasks)}개** · DONE: **{task_counts['DONE']}개** · 공수 가중 완료율: **{100 * completed_effort / total_effort:.1f}%**", '',
        f"Test: **{len(tests)}개** · PASS: **{test_counts['PASS']}개** · FAIL: **{test_counts['FAIL']}개** · NOT_RUN: **{test_counts['NOT_RUN']}개**", '',
        f'완료 공수 / 초기 기대 공수: {completed_effort:.2f} / {total_effort:.2f} 인일. 진척 산식이며 확정 일정이나 잔여 납기 예측이 아니다.', '',
        '## 2. Phase 현황', '',
        '| Phase | 상세 문서 | 선행 | Task 완료/전체 | Test PASS/전체 | Gate | 미해결 결정 |',
        '|---|---|---|---:|---:|---|---|'
    ]
    for p in phases:
        own_tasks = [x for x in tasks if x['phase'] == p['n']]
        own_tests = [x for x in tests if x.get('phase') == p['n']]
        pending = [d['id'] for d in decisions if p['n'] in d['phases'] and d['status'] not in ('원문기준 해결', '원문 해석 확정') and not d.get('approved_at')]
        lines.append(f"| P{p['n']} | [{p['title']}]({p['file']}) | {','.join('P'+str(n) for n in p['deps']) or '없음'} | {sum(t['status']=='DONE' for t in own_tasks)}/{len(own_tasks)} | {sum(t['status']=='PASS' for t in own_tests)}/{len(own_tests)} | {by_task[p['gate_task']]['status']} | {', '.join(pending) or '없음'} |")
    ready = [t for t in tasks if t['function'] in ACTIVE_FUNCTIONS and t['status'] == 'NOT_STARTED' and not t.get('blocked_by') and all(by_task[d]['status'] == 'DONE' for d in t['depends'])]
    lines += ['', '## 3. 현재 착수 가능한 Task', '', '현재 구현 활성화 Gate가 허용한 P0 기준선·빌드 lock 후보 중 선행 Task 및 `blocked_by`를 통과한 항목만 표시한다. 담당자·대상 파일·기존 API·실행 명령·증거 경로 확인 전에는 착수 확정이 아니다.', '']
    lines += [f"- **{t['id']}** — {t['name']}" for t in ready] or ['해당 항목 없음.']
    lines += ['', '## 4. 상태 정합성 경고', '']
    lines += ['- ' + x for x in inconsistencies] or ['현재 등록 상태 간 모순은 발견되지 않았다. 이것은 구현 완료나 테스트 통과를 뜻하지 않는다.']
    lines += ['', '## 5. 갱신 방법', '', '```text', 'py -3 검증도구/rebuild_status.py', 'py -3 검증도구/validate_docs.py', '```', '',
              '`tasks.json`의 owner/status/pr/actual_days와 `tests.json`의 status/actual/evidence를 갱신한다. 결정 승인 시 decisions.json과 해당 Task의 blocked_by도 함께 검토한다. 자동으로 미실행 테스트를 PASS로 만들지 않는다.', '',
              '[마스터](00_전체_구현_마스터_설계서.md) · [일정/리뷰](95_일정_및_검토운영.md) · [문서 검증](97_문서정합성_검증보고서.md)', '']
    (ROOT / '98_진행현황_대시보드.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps({'task_status': dict(task_counts), 'test_status': dict(test_counts), 'warnings': len(inconsistencies), 'ready_tasks': len(ready)}, ensure_ascii=False))
    return int(bool(inconsistencies))

if __name__ == '__main__':
    raise SystemExit(main())
