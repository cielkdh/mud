#!/usr/bin/env python3
"""관리 JSON에서 진행현황 Markdown을 다시 생성한다. Python 3.10+ / 외부 의존성 없음."""
from __future__ import annotations
import json
import re
from collections import Counter
from datetime import datetime, timezone, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def load(name: str):
    return json.loads((ROOT / '관리데이터' / f'{name}.json').read_text(encoding='utf-8'))


def atomic_assertions() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    text = (ROOT / '82_원자_요구사항_및_Assertion_추적표.md').read_text(encoding='utf-8')
    pattern = re.compile(r'^\| `(?P<id>AR-[^`]+)` \| (?P<source>.*?) \| (?P<line>\d+) \| (?P<shape>[A-Z_]+) \| (?P<modality>REQUIRED|DATA|RECOMMENDED|EXAMPLE|INFORMATIONAL) \| `(?P<function>FUNC-P\d+-\d+)` \| (?P<rest>.*)$')
    for line in text.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        tail = match.group('rest').rsplit(' | ', 3)
        if len(tail) != 4:
            continue
        assertion, _, _, status = tail
        row = match.groupdict()
        row.update(assertion=assertion, status=status.strip(' |`'))
        rows.append(row)
    return rows


def write_context_pack(task: dict, phase: dict, feature: dict, tests: dict[str, dict], decisions: dict[str, dict], assertions: list[dict[str, str]]) -> None:
    output = ROOT / '작업컨텍스트' / f"{task['id']}.md"
    output.parent.mkdir(exist_ok=True)
    own_assertions = [row for row in assertions if row['function'] == task['function'] and row['modality'] in ('REQUIRED', 'DATA')]
    command_rows = [line for line in (ROOT / '84_전체_Command_Event_계약서.md').read_text(encoding='utf-8').splitlines() if task['function'] in line]
    lines = [
        f"# {task['id']} 작업 컨텍스트", '',
        '> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.', '',
        '## Task', '',
        f"- 기능: `{task['function']}` {feature['name']}",
        f"- 단계/모듈: {task['stage']} / {task['module']}",
        f"- 선행 Task: {', '.join(task['depends']) or '없음'}",
        f"- 상세: {task['detail']}",
        f"- 완료 조건: {task['done']}", '',
        '## 기능 계약', '',
        f"- 메소드: `{feature['method']}`",
        f"- 대상 schema: {', '.join(feature['tables']) or '없음'}",
    ]
    for label, key in (('규칙', 'rules'), ('정상', 'normal'), ('경계', 'edge'), ('실패', 'failure')):
        lines.append(f"- {label}: {' / '.join(feature[key])}")
    lines += ['', '## 결정 의존', '']
    lines += [f"- {item}: {decisions[item]['status']} — {decisions[item]['proposal']}" for item in task.get('decision_dependencies', []) if item in decisions] or ['해당 항목 없음.']
    lines += ['', '## 관련 Test', '']
    lines += [f"- {item}: {tests[item]['input']} → {tests[item]['expected']} [{tests[item]['status']}]" for item in task['tests']]
    lines += ['', '## REQUIRED/DATA Atomic Assertions', '']
    lines += [f"- {row['id']} / {row['modality']} / {row['status']} / {row['source']} L{row['line']}: {row['assertion']}" for row in own_assertions] or ['해당 항목 없음.']
    lines += ['', '## Command/Event 계약', '']
    lines += command_rows or ['해당 항목 없음.']
    lines += ['', '## 권위 문서', '',
              f"- [Phase 상세](../{phase['file']})",
              '- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)',
              '- [Command/Event](../84_전체_Command_Event_계약서.md)',
              '- [Data Dictionary](../81_전체_데이터사전.md)', '']
    output.write_text('\n'.join(lines), encoding='utf-8')


def main() -> int:
    phases, tasks, tests, decisions = (load(x) for x in ('phases', 'tasks', 'tests', 'decisions'))
    by_task = {x['id']: x for x in tasks}
    by_test = {x['id']: x for x in tests}
    by_phase = {x['n']: x for x in phases}
    by_feature = {x['id']: x for x in load('functions')}
    by_decision = {x['id']: x for x in decisions}
    assertions = atomic_assertions()
    task_counts, test_counts = Counter(x['status'] for x in tasks), Counter(x['status'] for x in tests)
    inconsistencies: list[str] = []
    expected_assertions = load('document_manifest')['atomic_assertion_count']
    if len(assertions) != expected_assertions:
        inconsistencies.append(f'Atomic Assertion parse count {len(assertions)} != {expected_assertions}')
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
    active_functions = {function_id for phase in phases for function_id in phase.get('active_function_ids', [])}
    candidates = [t for t in tasks if t['function'] in active_functions and t['status'] == 'NOT_STARTED' and not t.get('blocked_by') and all(by_task[d]['status'] == 'DONE' for d in t['depends'])]
    approved = {'APPROVED_REQUIREMENT', 'IMPLEMENTED', 'VERIFIED'}
    pending_by_function = {function_id: [row for row in assertions if row['function'] == function_id and row['modality'] in ('REQUIRED', 'DATA') and row['status'] not in approved] for function_id in active_functions}
    atomic_blocked = [t for t in candidates if t['stage'] != '계약' and pending_by_function.get(t['function'])]
    ready = [t for t in candidates if t not in atomic_blocked]
    for task in ready:
        write_context_pack(task, by_phase[task['phase']], by_feature[task['function']], by_test, by_decision, assertions)
    lines += ['', '## 3. 현재 착수 가능한 Task', '', '`phases.json.active_function_ids`에 등록되고 선행 Task, `blocked_by`, 원자 Assertion Gate를 통과한 항목만 표시한다. 담당자·대상 파일·기존 API·실행 명령·증거 경로 확인 전에는 착수 확정이 아니다.', '']
    lines += [f"- **[{t['id']}](작업컨텍스트/{t['id']}.md)** — {t['name']}" for t in ready] or ['해당 항목 없음.']
    lines += ['', '## 4. 원자 Assertion Gate 대기', '']
    lines += [f"- **{t['id']}** — {t['function']}의 미승인 REQUIRED/DATA {len(pending_by_function[t['function']])}건" for t in atomic_blocked] or ['현재 선행 Task를 통과했으나 원자 Assertion 때문에 대기 중인 Task는 없다.']
    lines += ['', '## 5. 상태 정합성 경고', '']
    lines += ['- ' + x for x in inconsistencies] or ['현재 등록 상태 간 모순은 발견되지 않았다. 이것은 구현 완료나 테스트 통과를 뜻하지 않는다.']
    lines += ['', '## 6. 갱신 방법', '', '```text', 'py -3 검증도구/rebuild_status.py', 'py -3 검증도구/validate_docs.py', '```', '',
              '`tasks.json`의 owner/status/pr/actual_days와 `tests.json`의 status/actual/evidence를 갱신한다. 결정 승인 시 decisions.json과 해당 Task의 blocked_by도 함께 검토한다. 자동으로 미실행 테스트를 PASS로 만들지 않는다.', '',
              '[마스터](00_전체_구현_마스터_설계서.md) · [일정/리뷰](95_일정_및_검토운영.md) · [문서 검증](97_문서정합성_검증보고서.md)', '']
    (ROOT / '98_진행현황_대시보드.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps({'task_status': dict(task_counts), 'test_status': dict(test_counts), 'warnings': len(inconsistencies), 'ready_tasks': len(ready)}, ensure_ascii=False))
    return int(bool(inconsistencies))

if __name__ == '__main__':
    raise SystemExit(main())
