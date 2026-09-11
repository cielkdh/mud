#!/usr/bin/env python3
"""문서/참조/DAG/제안 SQLite DDL을 검증한다. 게임 구현이나 Room 테스트가 아니다.

Python 3.10+ 표준 라이브러리만 사용한다. 세이브 SQL은 메모리 DB에만 적용하며
사용자의 실제 게임 파일을 열거나 수정하지 않는다.
"""
from __future__ import annotations
import hashlib
import json
import re
import sqlite3
from collections import Counter, defaultdict, deque
from datetime import datetime, timezone, timedelta
from pathlib import Path
from urllib.parse import unquote
from rebuild_status import atomic_assertions

ROOT = Path(__file__).resolve().parents[1]
RESULTS: list[dict[str, object]] = []
DOMAIN_EVENT_FIELDS = ['eventId', 'sourceId', 'sourceEventId', 'sourceEpoch', 'sourceCommandId', 'sourceVersion', 'gameMinute', 'subMinuteMs', 'eventSequence', 'visibility', 'importance', 'payload']
GENERIC_TEST_FRAGMENTS = (
    '입력 fixture 생성 및 before snapshot/hash 보관',
    '실제 컴포넌트+Fake 외부port를 조립',
    '테스트용실제DB/파일adapter 구성',
    '권위쓰기 기능은 본 기능 표의 대상 테이블과 receipt',
)


def load(name: str):
    return json.loads((ROOT / '관리데이터' / f'{name}.json').read_text(encoding='utf-8'))


def record(name: str, failures: list[str] | None = None, detail: str = '') -> None:
    RESULTS.append({'name': name, 'status': 'FAIL' if failures else 'PASS', 'detail': detail, 'failures': failures or []})


def unique(items: list[dict], key: str, title: str) -> None:
    counts = Counter(x[key] for x in items)
    record(title, [str(k) for k, v in counts.items() if v > 1], f'{len(items)}개 ID 검사')


def unfenced(text: str) -> str:
    result: list[str] = []
    active: str | None = None
    for line in text.splitlines():
        match = re.match(r'^\s*(`{3,}|~{3,})', line)
        if match:
            token = match.group(1)
            if active is None:
                active = token[0]
            elif token[0] == active:
                active = None
            continue
        if active is None:
            result.append(line)
    return '\n'.join(result)


def markdown_table_rows(text: str) -> list[list[str]]:
    rows: list[list[str]] = []
    for line in text.splitlines():
        if not re.match(r'^\s*\|', line):
            continue
        cells = [cell.strip() for cell in line.strip().strip('|').split('|')]
        if cells and not all(re.fullmatch(r':?-{3,}:?', cell) for cell in cells):
            rows.append(cells)
    return rows


def assert_constraint(connection: sqlite3.Connection, sql: str, args=()) -> bool:
    connection.execute('SAVEPOINT constraint_probe')
    try:
        connection.execute(sql, args)
    except sqlite3.IntegrityError:
        connection.execute('ROLLBACK TO constraint_probe')
        connection.execute('RELEASE constraint_probe')
        return True
    except Exception:
        connection.execute('ROLLBACK TO constraint_probe')
        connection.execute('RELEASE constraint_probe')
        raise
    connection.execute('ROLLBACK TO constraint_probe')
    connection.execute('RELEASE constraint_probe')
    return False


def function_registry(contract: str, expected: set[str]) -> tuple[list[str], set[str], dict[str, set[str]], set[str]]:
    try:
        mutation_section = contract.split('## 6. ', 1)[1].split('## 7. ', 1)[0]
        nonmutation_section = contract.split('## 7. ', 1)[1].split('## 8. ', 1)[0]
    except IndexError:
        return ['6/7절 구분 누락'], set(), {}, set()
    mutation_ids = re.findall(r'`(FUNC-P\d+-\d+)`', mutation_section)
    nonmutation_ids = re.findall(r'`(FUNC-P\d+-\d+)`', nonmutation_section)
    mutation_counts, nonmutation_counts = Counter(mutation_ids), Counter(nonmutation_ids)
    failures = [f'{item}: registry count {mutation_counts[item] + nonmutation_counts[item]}' for item in sorted(expected) if mutation_counts[item] + nonmutation_counts[item] != 1]
    failures += [f'unknown: {item}' for item in sorted((set(mutation_ids) | set(nonmutation_ids)) - expected)]
    failures += [f'mutation duplicate: {item}' for item, count in sorted(mutation_counts.items()) if count > 1]
    failures += [f'non-mutation duplicate: {item}' for item, count in sorted(nonmutation_counts.items()) if count > 1]
    command_counts = Counter(re.findall(r'`(CMD-P\d+-F\d+)`', mutation_section))
    failures += [f'command duplicate: {item}' for item, count in sorted(command_counts.items()) if count > 1]
    function_commands: dict[str, set[str]] = defaultdict(set)
    for line in mutation_section.splitlines():
        commands = re.findall(r'`(CMD-P\d+-F\d+)`', line)
        functions = re.findall(r'`(FUNC-P\d+-\d+)`', line)
        for function in functions:
            function_commands[function].update(commands)
    failures += [f'mutation without command: {item}' for item in sorted(set(mutation_ids)) if not function_commands[item]]
    return failures, set(mutation_ids), function_commands, set(command_counts)


def screen_contract_checks(screen_contract: str, expected_functions: set[str], mutation_functions: set[str], function_commands: dict[str, set[str]], defined_commands: set[str]) -> tuple[list[str], list[str], list[str], int]:
    try:
        screen_registry = screen_contract.split('## 2. ', 1)[1].split('## 3. ', 1)[0]
        bottom_navigation = screen_contract.split('## 3. ', 1)[1].split('## 4. ', 1)[0]
    except IndexError:
        return ['2/3/4절 구분 누락'], ['2/3/4절 구분 누락'], [], 0
    nav_rows = [row for row in markdown_table_rows(bottom_navigation) if row[0] != '기본 탭']
    tabs = [row[0].strip('`') for row in nav_rows]
    navigation_failures = [] if tabs == ['홈', '던전', '파티', '도시', '기록'] else [str(tabs)]
    action_failures: list[str] = []
    screen_rows = [row for row in markdown_table_rows(screen_registry) if row[0] != 'Screen ID']
    screen_ids = Counter(row[0] for row in screen_rows if row)
    action_failures += [f'duplicate screen: {item}' for item, count in sorted(screen_ids.items()) if count > 1]
    referenced_commands: set[str] = set()
    for row in screen_rows:
        if len(row) != 9:
            action_failures.append(f'{row[0] if row else "?"}: expected 9 cells, got {len(row)}')
            continue
        screen_id, execution = row[0], row[8]
        commands = set(re.findall(r'`(CMD-P\d+-F\d+)`', execution))
        functions = set(re.findall(r'(FUNC-P\d+-\d+)', execution))
        referenced_commands.update(commands)
        action_failures += [f'{screen_id}: unknown function {item}' for item in sorted(functions - expected_functions)]
        action_failures += [f'{screen_id}: unknown command {item}' for item in sorted(commands - defined_commands)]
        if not re.search(r'CMD-P\d+-F\d+|QUERY|NAVIGATION|LOCAL_[A-Z_]+|TOOL(?:_QUERY)?', execution):
            action_failures.append(f'{screen_id}: unknown execution contract {execution}')
        if re.search(r'(^|/)(MUTATING|DIRTY)(/|$)', row[4]) and not commands:
            action_failures.append(f'{screen_id}: mutation state has no command')
        for function in sorted(functions & mutation_functions):
            if not (commands & function_commands.get(function, set())):
                action_failures.append(f'{screen_id}: mutation {function} has no matching command')
    return navigation_failures, action_failures, sorted(referenced_commands), len(screen_rows)


def test_detail_failures(case: dict, markers: tuple[str, ...]) -> list[str]:
    text = '\n'.join(str(case.get(key, '')) for key in ('precondition', 'steps', 'db', 'logs', 'success'))
    failures = [f'범용 템플릿 잔존: {fragment}' for fragment in GENERIC_TEST_FRAGMENTS if fragment in text]
    failures += [f'실행 증거 marker 누락: {marker}' for marker in markers if marker not in text]
    return failures


def event_contract_checks(contract: str, save_ddl: str) -> None:
    failures: list[str] = []
    block = re.search(r'data class DomainEvent<[^>]+>\s*\((.*?)\n\)', contract, re.S)
    dto_fields = re.findall(r'\bval\s+(\w+)\s*:', block.group(1)) if block else []
    if dto_fields != DOMAIN_EVENT_FIELDS:
        failures.append(f'DomainEvent fields: {dto_fields}')
    if '(gameMinute, subMinuteMs, sourceVersion, sourceEpoch, sourceCommandId, eventSequence, eventId)' not in contract:
        failures.append('replay sort key missing')
    if 'versioned `EventCodecId`' not in contract or 'CombatCompleted.v1' not in contract or 'decoder/upcaster' not in contract:
        failures.append('versioned EventCodecId/decoder/upcaster contract missing')
    expected_columns = {'id', 'source_id', 'source_event_id', 'source_epoch', 'source_command_id', 'source_version', 'event_type', 'event_sequence', 'game_minute', 'sub_ms', 'visibility', 'importance', 'payload_json'}
    try:
        with sqlite3.connect(':memory:') as con:
            con.executescript(save_ddl)
            columns = {row[1] for row in con.execute('PRAGMA table_info(world_event)')}
            failures += [f'world_event column missing: {item}' for item in sorted(expected_columns - columns)]
            fk_pairs = {(row[3], row[4]) for row in con.execute('PRAGMA foreign_key_list(world_event)') if row[2] == 'command_receipt'}
            if not {('source_epoch', 'epoch'), ('source_command_id', 'command_id')} <= fk_pairs:
                failures.append(f'command_receipt composite FK missing: {sorted(fk_pairs)}')
            for receipt_id, command_id, epoch, version in (('r1', 'cmd', 'e1', 7), ('r2', 'cmd', 'e2', 8), ('r3', 'other', 'e1', 9)):
                con.execute('INSERT INTO command_receipt(id,command_id,epoch,payload_hash,result_code,result_json,state_version,game_minute) VALUES(?,?,?,?,?,?,?,?)', (receipt_id, command_id, epoch, 'h', 'OK', '{}', version, 10))
            values = ('ev1', 'actor', 'origin', 'e1', 'cmd', 7, 'TestEvent.v1', 0, 10, 123, 'PUBLIC', 50, '{"value":1}')
            con.execute('INSERT INTO world_event(id,source_id,source_event_id,source_epoch,source_command_id,source_version,event_type,event_sequence,game_minute,sub_ms,visibility,importance,payload_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)', values)
            selected = con.execute('SELECT id,source_id,source_event_id,source_epoch,source_command_id,source_version,event_type,event_sequence,game_minute,sub_ms,visibility,importance,payload_json FROM world_event WHERE id=?', ('ev1',)).fetchone()
            if selected != values:
                failures.append(f'roundtrip mismatch: {selected}')
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*\.v[1-9][0-9]*', selected[6]):
                failures.append(f'event codec id format: {selected[6]}')
            con.execute("INSERT INTO world_event(id,source_epoch,source_command_id,source_version,event_type,event_sequence,game_minute,sub_ms,visibility,importance,payload_json) VALUES('ev2','e2','cmd',8,'TestEvent.v1',0,10,123,'PUBLIC',50,'{}')")
            ordered = [row[0] for row in con.execute('SELECT id FROM world_event ORDER BY game_minute,sub_ms,source_version,source_epoch,source_command_id,event_sequence,id')]
            if ordered != ['ev1', 'ev2']:
                failures.append(f'event order mismatch: {ordered}')
            probes = (
                ('sub_ms -1', "UPDATE world_event SET sub_ms=-1 WHERE id='ev1'"),
                ('sub_ms 60000', "UPDATE world_event SET sub_ms=60000 WHERE id='ev1'"),
                ('event_sequence -1', "UPDATE world_event SET event_sequence=-1 WHERE id='ev1'"),
                ('source_version -1', "UPDATE world_event SET source_version=-1 WHERE id='ev1'"),
                ('source command sequence duplicate', "INSERT INTO world_event(id,source_epoch,source_command_id,source_version,event_type,event_sequence,game_minute,sub_ms,visibility,importance,payload_json) VALUES('ev3','e1','cmd',7,'TestEvent',0,10,123,'PUBLIC',50,'{}')"),
                ('missing command receipt', "UPDATE world_event SET source_epoch='missing' WHERE id='ev1'")
            )
            failures += [name for name, sql in probes if not assert_constraint(con, sql)]
    except Exception as exc:
        failures.append(f'{type(exc).__name__}: {exc}')
    record('DomainEvent 밀리초·완전 순서·왕복·codec 계약', failures, 'DTO 12개 필드와 world_event 대응 열, versioned EventCodecId, 범위/명령별 순서/command receipt FK를 실제 SQLite 왕복으로 확인')


def validator_self_checks() -> None:
    expected = {'FUNC-P9-002'}
    duplicate_contract = '## 6. Mutation\n| `CMD-P9-F002` | `FUNC-P9-002` |\n| `CMD-P9-F003` | `FUNC-P9-002` |\n## 7. Query\n## 8. End'
    duplicate_failures, _, _, _ = function_registry(duplicate_contract, expected)
    valid_registry = '## 6. Mutation\n| `CMD-P9-F002` | P9 | `FUNC-P9-002` | x | x | x | x | x |\n## 7. Query\n## 8. End'
    _, mutations, commands_by_function, commands = function_registry(valid_registry, expected)
    invalid_screen = '## 2. Registry\n| Screen ID | 화면 | Route | Owner | 상태 | 대표 Action | Destination | Guard | 실행 계약 |\n| SCR-X | 지도 | x | P9 | READY/MUTATING | 주석 저장 | x | x | `QUERY` |\n## 3. Navigation\n| 기본 탭 | Root | Back |\n| 홈 | x | x |\n| 던전 | x | x |\n| 파티 | x | x |\n| 도시 | x | x |\n| 기록 | x | x |\n| 미지탭 | x | x |\n## 4. Rules'
    nav_failures, action_failures, _, _ = screen_contract_checks(invalid_screen, expected, mutations, commands_by_function, commands)
    failures = []
    if not any('duplicate' in item or 'count 2' in item for item in duplicate_failures):
        failures.append('same-section function duplicate not detected')
    if not nav_failures:
        failures.append('unknown sixth tab not detected')
    if not any('mutation state has no command' in item for item in action_failures):
        failures.append('mutation state without command not detected')
    if not test_detail_failures({'steps': GENERIC_TEST_FRAGMENTS[0]}, ('WorldSession.execute',)):
        failures.append('generic critical test template not detected')
    if test_detail_failures({'steps': 'WorldSession.execute', 'db': '구체 행·hash 비교'}, ('WorldSession.execute',)):
        failures.append('specific critical test rejected')
    record('검증기 false-negative 회귀', failures, 'Function 중복·미지 탭·MUTATING CMD 누락·핵심 Test 범용 템플릿을 synthetic fixture로 거절')


def sql_checks() -> None:
    for store, file in (('content', '02_제안_content_schema.sql'), ('save', '03_제안_save_schema.sql')):
        with sqlite3.connect(':memory:') as con:
            try:
                con.executescript((ROOT / '설계부록' / file).read_text(encoding='utf-8'))
                tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
                missing: list[str] = []
                for table in tables:
                    for fk in con.execute(f'PRAGMA foreign_key_list("{table}")'):
                        if fk[2] not in tables:
                            missing.append(f'{table} -> {fk[2]}')
                fk_errors = list(con.execute('PRAGMA foreign_key_check'))
                integrity = con.execute('PRAGMA integrity_check').fetchone()[0]
                record(f'SQL-{store}: DDL 구문·FK 대상·무결성', missing + [str(x) for x in fk_errors] + ([] if integrity == 'ok' else [str(integrity)]), f'SQLite {sqlite3.sqlite_version}; {len(tables)}개 테이블 생성. Room/KSP/Android 실행은 아님.')
                if store != 'save':
                    continue
                # 1. Conditional debit + exactly-once receipt modeled with the proposed constraints.
                con.execute("INSERT INTO money_account(id,owner_kind,owner_id,purpose,balance) VALUES('a','MERCENARY','npc1','CARRIED',100)")
                con.commit()
                with con:
                    count = con.execute("UPDATE money_account SET balance=balance-40 WHERE id='a' AND balance-reserved>=40").rowcount
                    con.execute("INSERT INTO command_receipt(id,command_id,epoch,payload_hash,result_code,result_json,state_version,game_minute) VALUES('r1','cmd1','e1','h1','OK','{}',1,0)")
                first = con.execute("SELECT balance FROM money_account WHERE id='a'").fetchone()[0]
                try:
                    with con:
                        con.execute("UPDATE money_account SET balance=balance-40 WHERE id='a' AND balance-reserved>=40")
                        con.execute("INSERT INTO command_receipt(id,command_id,epoch,payload_hash,result_code,result_json,state_version,game_minute) VALUES('r2','cmd1','e1','h1','OK','{}',2,0)")
                except sqlite3.IntegrityError:
                    pass
                second = con.execute("SELECT balance FROM money_account WHERE id='a'").fetchone()[0]
                record('SQL-단건 원자성: 중복 receipt와 차감 롤백', [] if (count,first,second)==(1,60,60) else [str((count,first,second))], '100→60; 중복 UNIQUE 실패 후 추가 차감은 롤백되어 60 유지')
                count = con.execute("UPDATE money_account SET balance=balance-100 WHERE id='a' AND balance-reserved>=100").rowcount
                amount = con.execute("SELECT balance FROM money_account WHERE id='a'").fetchone()[0]
                record('SQL-잔액 경계: 부족금액 조건부 갱신', [] if (count,amount)==(0,60) else [str((count,amount))], '100 차감 요청의 영향행 0; 잔액 60 유지')
                record('SQL-CHECK: 음수 잔액 차단', [] if assert_constraint(con,"UPDATE money_account SET balance=-1 WHERE id='a'") else ['음수 잔액이 허용됨'])
                # 2. Whole-generation manifests reference actual historical chunk bytes.
                for idx, value in ((1,100),(2,60)):
                    payload = json.dumps({'balance':value}, separators=(',',':')).encode()
                    con.execute('INSERT INTO checkpoint_chunk(id,sha256,codec_version,encoding,uncompressed_bytes,payload) VALUES(?,?,1,\'json\',?,?)', (f'ch{idx}', hashlib.sha256(payload).hexdigest(),len(payload),payload))
                    con.execute("INSERT INTO save_generation(id,parent_id,branch_id,generation_no,game_minute,schema_version,content_version,balance_version,manifest_hash,status) VALUES(?,?,'b',?,0,1,'c1','b1',?,'COMMITTED')",(f'g{idx}',None if idx==1 else 'g1',idx,f'm{idx}'))
                    con.execute("INSERT INTO generation_chunk(id,generation_id,domain_key,shard_no,chunk_id) VALUES(?,?,'money',0,?)",(f'gc{idx}',f'g{idx}',f'ch{idx}'))
                got = []
                for generation in ('g1','g2'):
                    row=con.execute('SELECT cc.payload FROM generation_chunk gc JOIN checkpoint_chunk cc ON cc.id=gc.chunk_id WHERE gc.generation_id=?',(generation,)).fetchone()
                    got.append(json.loads(row[0])['balance'])
                record('SQL-과거 세대 바이트 조회', [] if got==[100,60] else [str(got)], 'g1=100, g2=60을 서로 다른 실제 chunk에서 조회. 전체 게임 복원 알고리즘 실행은 아님.')
                record('SQL-FK: 참조 중 chunk 삭제 방지', [] if assert_constraint(con,"DELETE FROM checkpoint_chunk WHERE id='ch1'") else ['참조 중 chunk 삭제 허용됨'])
                # 3. Default stat key eliminates SQLite UNIQUE+NULL duplicate-event loophole.
                con.execute("INSERT INTO mercenary(id,sex_code,given_name,family_name,display_name,name_generator_version,birth_game_day,culture_id,portrait_image_key,portrait_pool_version,class_id,level,experience,lifecycle_status) VALUES('npc1','M','카엘','발렌','카엘 발렌','v1',0,'c1','NPC-M-00001','p1','sword',1,0,'ACTIVE')")
                con.execute("INSERT INTO growth_ledger(id,mercenary_id,source_event_id,growth_kind,level_at_event,delta_value,reversible) VALUES('gr1','npc1','ev1','FREE_POINT',2,1,1)")
                ok=assert_constraint(con,"INSERT INTO growth_ledger(id,mercenary_id,source_event_id,growth_kind,level_at_event,delta_value,reversible) VALUES('gr2','npc1','ev1','FREE_POINT',2,1,1)")
                record('SQL-성장 원장: 기본 stat key 중복 차단', [] if ok else ['같은 성장 사건 중복 허용됨'], "stat_key NOT NULL DEFAULT 'NONE'과 복합 UNIQUE 검증")
                record('SQL-데이터 삽입 후 무결성', [str(x) for x in con.execute('PRAGMA foreign_key_check')] + ([] if con.execute('PRAGMA integrity_check').fetchone()[0]=='ok' else ['integrity error']))
            except Exception as exc:
                record(f'SQL-{store}: 실행 오류', [f'{type(exc).__name__}: {exc}'])


def main() -> int:
    manifest, phases, features, tasks, tests, reqs = (load(n) for n in ('document_manifest','phases','functions','tasks','tests','requirements'))
    by_phase={x['n']:x for x in phases}; by_func={x['id']:x for x in features}; by_task={x['id']:x for x in tasks}; by_test={x['id']:x for x in tests}
    source=(ROOT/manifest['source_file']).read_bytes()
    record('원본 SHA-256 보존', [] if hashlib.sha256(source).hexdigest()==manifest['source_sha256'] else ['원본 해시 변경'])
    section_ids=[int(x) for x in re.findall(r'^# (\d+)\. ',source.decode('utf-8'),re.M)]
    record('원문 절 전체 매핑', [] if sorted(section_ids)==sorted(r['section'] for r in reqs)==list(range(1,3136)) else ['절 번호 누락/중복'], f'{len(reqs)}개 절 묶음. 하위규칙 의미 검토는 포함하지 않음.')
    for items,key,title in ((phases,'n','Phase ID 고유성'),(features,'id','기능 ID 고유성'),(tasks,'id','Task ID 고유성'),(tests,'id','Test ID 고유성'),(reqs,'id','요구 묶음 ID 고유성')):
        unique(items,key,title)
    expected={'phases':len(phases),'functions':len(features),'tasks':len(tasks),'phase_tests':sum(t.get('phase') in by_phase for t in tests),'global_tests':sum(t.get('phase') not in by_phase for t in tests)}
    record('마스터 수량 정합성',[f'{k}: {manifest[k]} != {v}' for k,v in expected.items() if manifest[k]!=v],json.dumps(expected,ensure_ascii=False))
    failures=[]
    for r in reqs:
        if r['phase'] not in by_phase or r['function'] not in by_func: failures.append(r['id']+': phase/function')
        if not r.get('tasks') or not r.get('tests'): failures.append(r['id']+': empty links')
        failures += [r['id']+': '+x for x in r.get('tasks',[]) if x not in by_task]
        failures += [r['id']+': '+x for x in r.get('tests',[]) if x not in by_test]
        if r['function'] in by_func and by_func[r['function']]['p'] != r['phase']: failures.append(r['id']+': phase mismatch')
        if r['phase'] in by_phase:
            body=(ROOT/by_phase[r['phase']]['file']).read_text(encoding='utf-8')
            if f'id="src-{r["section"]:04d}"' not in body: failures.append(r['id']+': source appendix missing')
    record('요구→Phase→기능→Task→Test 연결',failures)
    failures=[]
    for f in features:
        failures += [f['id']+': '+x for x in f['task_ids'] if x not in by_task]
        failures += [f['id']+': '+x for x in f['test_ids'] if x not in by_test]
        if not f['sources'] and not f.get('related_sources'): failures.append(f['id']+': no source evidence')
    record('기능별 작업·테스트·근거 연결',failures)
    contract=(ROOT/'84_전체_Command_Event_계약서.md').read_text(encoding='utf-8')
    save_ddl=(ROOT/'설계부록/03_제안_save_schema.sql').read_text(encoding='utf-8')
    expected_functions={x['id'] for x in features}
    event_contract_checks(contract, save_ddl)
    p0_body=(ROOT/by_phase[0]['file']).read_text(encoding='utf-8')
    p0_contract=p0_body.split('<a id="func-p0-003"></a>',1)[1].split('<a id="func-p0-004"></a>',1)[0]
    event_rule='DomainEvent는'+'/'.join(DOMAIN_EVENT_FIELDS)+'를포함한다'
    p0_feature=next(x for x in by_phase[0]['features'] if x['id']=='FUNC-P0-003')
    event_sync_sources={
        'P0 상세설계': re.sub(r'\s+','',p0_contract),
        'functions.json': ''.join(by_func['FUNC-P0-003']['rules']),
        'phases.json': ''.join(p0_feature['rules']),
        'tasks.json': by_task['P0-TASK-012']['detail'],
    }
    record('P0→전역 DomainEvent 계약 동기화',
           [name+': 12개 필드 계약 누락' for name,text in event_sync_sources.items() if event_rule not in re.sub(r'\s+','',text)]
           + (['P0 상세설계: 구형 gameTime 계약 잔존'] if 'gameTime' in p0_contract else []),
           '/'.join(DOMAIN_EVENT_FIELDS))
    classification_failures, mutation_functions, function_commands, defined_commands = function_registry(contract, expected_functions)
    record('Mutation과 Query/Tool 기능 분류',classification_failures,
           f'mutation {len(mutation_functions)}개 / non-mutation {len(expected_functions-mutation_functions)}개')
    state_machine=(ROOT/'83_전체_상태머신_설계서.md').read_text(encoding='utf-8')
    expected_classes={function_id:'AUTHORITATIVE' for function_id in expected_functions}
    nonmutation_section=contract.split('## 7. ',1)[1].split('## 8. ',1)[0]
    for row in markdown_table_rows(nonmutation_section):
        function_match=re.search(r'(FUNC-P\d+-\d+)',row[0])
        if not function_match or len(row)<3:
            continue
        kind=row[1].strip('`').lower()
        durable=row[2].lower()
        expected_classes[function_match.group(1)]=(
            'BUILD_ARTIFACT' if 'build 산출물' in durable else
            'PROJECTION' if 'consumer' in kind else
            'LIFECYCLE' if 'lifecycle' in kind else
            'READ' if kind in ('read','compute') or 'read/' in kind else
            'TOOL'
        )
    registry_section=state_machine.split('## 4. 전체 기능 상태 Registry',1)[1].split('## 5.',1)[0]
    actual_classes={}
    for row in markdown_table_rows(registry_section):
        function_match=re.fullmatch(r'`(FUNC-P\d+-\d+)`',row[1]) if len(row)==5 else None
        if function_match:
            actual_classes[function_match.group(1)]=row[4].strip('`')
    persistence_failures=[f'{function_id}: {actual_classes.get(function_id)} != {expected}' for function_id,expected in sorted(expected_classes.items()) if actual_classes.get(function_id)!=expected]
    record('State Registry PersistenceClass→Command/Event 동기화',persistence_failures,
           'AUTHORITATIVE/PROJECTION/READ/TOOL/LIFECYCLE/BUILD_ARTIFACT 6종을 84 계약에서 파생')
    screen_contract=(ROOT/'88_화면_ID_상태_Action_전이_Matrix.md').read_text(encoding='utf-8')
    screen_report=(ROOT/'99_전역기준문서_추가_정합성_검증보고서.md').read_text(encoding='utf-8')
    nfr=(ROOT/'85_NFR_성능_용량_단말_기준서.md').read_text(encoding='utf-8')
    balance=(ROOT/'86_밸런스_KPI_및_시뮬레이션_합격기준.md').read_text(encoding='utf-8')
    nfr_decision=(ROOT/'94_설계보완안_및_결정대장.md').read_text(encoding='utf-8')
    nfr_markers=('85/C19','BASELINE_V1','NOT_RUN','86','PROVISIONAL/설계 보완안')
    record('NFR 승인 상태 동기화',
           [marker+' 누락' for marker in nfr_markers if marker not in screen_report]
           + ['85: '+marker+' 누락' for marker in ('C19','BASELINE_V1','NOT_RUN') if marker not in nfr]
           + ['94 C19: '+marker+' 누락' for marker in ('C19','BASELINE_V1','NOT_RUN') if marker not in nfr_decision]
           + (['86: PROVISIONAL 누락'] if 'PROVISIONAL/설계 보완안' not in balance else [])
           + (['85/86 일괄 PROVISIONAL 문구 잔존'] if '85/86의 신규 정량 수치는' in screen_report else []),
           '85/C19=BASELINE_V1·실측 NOT_RUN, 86 신규 정량 규칙=PROVISIONAL')
    navigation_failures, action_failures, referenced_commands, screen_count = screen_contract_checks(screen_contract, expected_functions, mutation_functions, function_commands, defined_commands)
    if manifest.get('screen_count') != screen_count:
        action_failures.append(f'document_manifest screen_count {manifest.get("screen_count")} != {screen_count}')
    if f'{screen_count}개 Screen ID' not in screen_contract:
        action_failures.append(f'Definition of Done screen count != {screen_count}')
    if f'Screen IDs: **{screen_count}**' not in screen_report:
        action_failures.append(f'global consistency report screen count != {screen_count}')
    record('기본 Bottom Navigation 5탭', navigation_failures, '홈/던전/파티/도시/기록')
    record('Screen Action→Mutation 계약 연결', action_failures, f'{screen_count}개 Screen / {len(referenced_commands)}개 Command ID 참조')
    master=(ROOT/'00_전체_구현_마스터_설계서.md').read_text(encoding='utf-8')
    p6_body=(ROOT/by_phase[6]['file']).read_text(encoding='utf-8')
    task_plan=(ROOT/'90_전체_Task_목록.md').read_text(encoding='utf-8')
    test_plan=(ROOT/'91_전체_Test_계획서.md').read_text(encoding='utf-8')
    master_failures=[]
    headings=[int(x) for x in re.findall(r'^## (\d+)\. ',master,re.M)]
    if headings != list(range(1,14)):
        master_failures.append(f'마스터 절 번호: {headings}')
    early_sources={
        '마스터': master,
        'P6 상세설계': p6_body,
        'phases.json': by_phase[6]['gate'],
        'tasks.json': by_task['P6-TASK-031']['detail']+by_task['P6-TASK-031']['done'],
        'tests.json': by_test['P6-IT-007']['title']+by_test['P6-IT-007']['expected'],
        '전체 Task': task_plan,
        '전체 Test': test_plan,
    }
    master_failures += [name+': Early Playable Gate 누락' for name,text in early_sources.items() if 'Early Playable Gate' not in text]
    record('마스터 Early Playable Gate·절 번호 동기화', master_failures, 'P6 기존 Gate/Test 재사용; 마스터 절 1..13')
    p3_body=(ROOT/by_phase[3]['file']).read_text(encoding='utf-8')
    p23_body=(ROOT/by_phase[23]['file']).read_text(encoding='utf-8')
    p21_body=(ROOT/by_phase[21]['file']).read_text(encoding='utf-8')
    p22_body=(ROOT/by_phase[22]['file']).read_text(encoding='utf-8')
    p25_body=(ROOT/by_phase[25]['file']).read_text(encoding='utf-8')
    schema_registry=(ROOT/'관리데이터/schema_registry.json').read_text(encoding='utf-8')
    schema_objects=json.loads(schema_registry)
    dictionary=(ROOT/'81_전체_데이터사전.md').read_text(encoding='utf-8')
    data_contract_markers=('Meaning','Unit','Enum/code set','Range/check','Default/nullability','Writer','Reader','Lifecycle/delete rule','Retention/hash/codec scope')
    alias_failures=[f'{alias}: {schema_objects.get(alias,{}).get("alias")} != {target}' for alias,target in (('storage','storage_location'),('wallet','money_account')) if schema_objects.get(alias,{}).get('alias')!=target]
    alias_failures += [marker+' 누락' for marker in data_contract_markers if marker not in dictionary]
    for alias,target in (('storage','storage_location'),('wallet','money_account')):
        alias_block=dictionary.split(f'. `{alias}`',1)[1].split('### 3.',1)[0]
        if f'공통 alias → `{target}`' not in alias_block or '비물리 alias. Entity/DAO/table 생성 금지.' not in alias_block:
            alias_failures.append(f'{alias}: 데이터사전 alias/비물리 계약 누락')
    record('Data Dictionary authoritative 필드·alias 계약',alias_failures,
           'authoritative save 9개 속성 선확정; storage→storage_location, wallet→money_account는 비물리 alias')
    save_boundary_sources={
        'Command/Event 계약': contract,
        'P3 상세설계': p3_body,
        'functions.json': ''.join(by_func['FUNC-P3-001']['rules']),
        'phases.json': ''.join(next(x for x in by_phase[3]['features'] if x['id']=='FUNC-P3-001')['rules']),
        'tasks.json': by_task['P3-TASK-001']['detail']+by_task['P3-TASK-001']['done'],
        'tests.json': by_test['P3-CT-001']['input']+by_test['P3-CT-001']['expected'],
        '전체 Task': task_plan,
        '전체 Test': test_plan,
    }
    record('외부 Command→내부 SaveCoordinator 단일 receipt 경계',
           [name+': 내부 SaveCoordinator/단일 receipt 계약 누락' for name,text in save_boundary_sources.items()
            if 'SaveCoordinator' not in text or not any(marker in text for marker in ('중첩', '내부 P3', 'receipt 1개', 'receipt는 바깥 command에만 1개', 'receipt를 만들지'))],
           'CheckpointWorld 외부 command 1개당 receipt 1개; 내부 SaveCoordinator command/receipt/Event 0개')
    event_codec_requirements={
        'Command/Event 계약': ('EventCodecId', 'decoder/upcaster'),
        'P3 상세설계': ('EventCodecId', 'decoder/upcaster'),
        'P21 상세설계': ('EventCodecId', 'decode/upcast'),
        'P25 상세설계': ('EventCodecId', 'decode/upcast', 'projection'),
        'schema_registry.json': ('EventCodecId', 'decoder/upcaster'),
        'P25-IT-002': ('CombatCompleted.v1', 'decode/upcast', 'projection'),
    }
    event_codec_sources={
        'Command/Event 계약': contract,
        'P3 상세설계': p3_body,
        'P21 상세설계': p21_body,
        'P25 상세설계': p25_body,
        'schema_registry.json': schema_registry,
        'P25-IT-002': by_test['P25-IT-002']['input']+by_test['P25-IT-002']['expected'],
    }
    record('Versioned EventCodec·구 event 재구축 계약',
           [name+': '+marker+' 누락' for name,markers in event_codec_requirements.items() for marker in markers if marker not in event_codec_sources[name]],
           '기존 event_type 의미 고정, decoder/upcaster 보존, projection staging/rebuild')
    phase_text='\n'.join((ROOT/p['file']).read_text(encoding='utf-8') for p in phases)
    obsolete_patterns=(
        r'^\| [^|\n]+Validator \| 신규/기존공통확장 \|',
        r'^\| [^|\n]+RepositoryPort \| 공통 Repository 확장 \|',
        r'^\| [^|\n]+Projection \| 화면또는 report 확장 \|',
    )
    responsibility_rows=(
        '| 입력 검증 | UseCase 내부 또는 기존 도메인 정책 재사용 |',
        '| 저장 경계 | 기존 Aggregate별 typed Port/SavePort 재사용 |',
        '| 표현 변환 | 기존 feature mapper 또는 순수 함수 재사용 |',
    )
    expected_responsibility_rows=len(features)-len(by_phase[0]['features'])
    component_failures=[pattern for pattern in obsolete_patterns if re.search(pattern, phase_text, re.M)]
    component_failures += [f'{row}: {phase_text.count(row)} != {expected_responsibility_rows}' for row in responsibility_rows if phase_text.count(row)!=expected_responsibility_rows]
    record('기능별 기계적 Port/Validator/Projection 제거', component_failures,
           f'P0 기반 기능 4개를 제외한 {expected_responsibility_rows}개 게임 기능은 UseCase 검증·Aggregate typed Port·기존 mapper를 기본 재사용')
    allowlist_sources={
        '마스터': master,
        'P0 상세설계': p0_body,
        'functions.json': ''.join(by_func['FUNC-P0-002']['rules']),
        'phases.json': ''.join(next(x for x in by_phase[0]['features'] if x['id']=='FUNC-P0-002')['rules']),
        'tasks.json': by_task['P0-TASK-007']['detail']+by_task['P0-TASK-007']['done'],
        'tests.json': by_test['P0-BT-002']['input']+by_test['P0-BT-002']['expected'],
        '전체 Test': test_plan,
    }
    record('전체 모듈 허용 의존성 allowlist 계약',
           [name+': allowlist/금지 edge 계약 누락' for name,text in allowlist_sources.items()
            if 'allowlist' not in text and not ('금지 edge' in text and any(marker in text for marker in ('허용 edge', '정상 edge')))],
           '표 밖 내부 edge, core→feature/app, feature→feature, simulation→Android/Room/Compose/네트워크 금지')
    p0_ownership_sources={
        '마스터': master,
        'P0 상세설계': p0_body,
        'Command/Event 계약': contract,
        'functions.json': ''.join(by_func['FUNC-P0-002']['rules']),
        'phases.json': ''.join(next(x for x in by_phase[0]['features'] if x['id']=='FUNC-P0-002')['rules']),
        'tasks.json': by_task['P0-TASK-007']['detail']+by_task['P0-TASK-011']['detail'],
    }
    p3_ownership_sources={
        '마스터': master,
        'P3 상세설계': p3_body,
        'Command/Event 계약': contract,
        'functions.json': by_func['FUNC-P3-001']['method']+''.join(by_func['FUNC-P3-001']['rules']),
        'phases.json': next(x for x in by_phase[3]['features'] if x['id']=='FUNC-P3-001')['method']+''.join(next(x for x in by_phase[3]['features'] if x['id']=='FUNC-P3-001')['rules']),
        'tasks.json': by_task['P3-TASK-001']['detail'],
    }
    ownership_failures=[name+': '+marker+' 누락' for name,text in p0_ownership_sources.items() for marker in ('WorldSession', 'SavePort', ':core:simulation', ':app') if marker not in text]
    ownership_failures += [name+': '+marker+' 누락' for name,text in p3_ownership_sources.items() for marker in ('SaveCoordinator', ':core:save') if marker not in text]
    if 'internal SaveCoordinator.commit' in by_func['FUNC-P3-001']['method'] or 'internal SaveCoordinator.commit' in next(x for x in by_phase[3]['features'] if x['id']=='FUNC-P3-001')['method']:
        ownership_failures.append('관리데이터: WorldEngine→SaveCoordinator 구체 의존 잔존')
    boundary_test='\n'.join(str(by_test[test_id].get(key,'')) for test_id in ('P0-BT-002','P0-IT-002') for key in ('input','expected','precondition','steps','db','logs','success'))
    ownership_failures += ['P0 경계 Test: '+marker+' 누락' for marker in ('forbiddenSymbol', 'Gradle', 'applicationId', 'Room schema') if marker not in boundary_test]
    record('WorldSession·SavePort 물리 소유와 조립 루트', ownership_failures,
           'P0 :app/:core:simulation 계약; P3 :core:save/SaveCoordinator 구현; headless는 P23/P25까지 유예')
    decision_doc=(ROOT/'94_설계보완안_및_결정대장.md').read_text(encoding='utf-8')
    schedule=(ROOT/'95_일정_및_검토운영.md').read_text(encoding='utf-8')
    decisions_text=(ROOT/'관리데이터/decisions.json').read_text(encoding='utf-8')
    activation_sources={'마스터':master, 'P0 상세설계':p0_body, '전체 Task':task_plan, '일정':schedule, '결정대장':decision_doc, 'decisions.json':decisions_text}
    record('P0 우선 구현 활성화 Gate',
           [name+': P0/추적/활성화 계약 누락' for name,text in activation_sources.items()
            if 'P0' not in text or '추적' not in text or '활성' not in text],
           '672개 Task는 추적 후보; 파일/API/담당/명령/증거/결정과 P0 lock 전에는 P1~P25 비활성')
    assertions=atomic_assertions()
    active_functions={function_id for phase in phases for function_id in phase.get('active_function_ids', [])}
    approved_states={'APPROVED_REQUIREMENT','IMPLEMENTED','VERIFIED'}
    pending_by_function={function_id:[row for row in assertions if row['function']==function_id and row['modality'] in ('REQUIRED','DATA') and row['status'] not in approved_states] for function_id in active_functions}
    atomic_failures=[] if len(assertions)==manifest['atomic_assertion_count'] else [f'Atomic parse count {len(assertions)} != {manifest["atomic_assertion_count"]}']
    atomic_failures += [task['id']+': 미승인 REQUIRED/DATA 상태에서 '+task['status'] for task in tasks if task['function'] in active_functions and task['stage']!='계약' and task['status'] in ('IN_PROGRESS','DONE') and pending_by_function.get(task['function'])]
    atomic_failures += [task['id']+': 계약 Task 완료 조건에 Assertion Gate 누락' for task in tasks if task['function'] in active_functions and task['stage']=='계약' and not all(marker in task['detail']+task['done'] for marker in ('REQUIRED/DATA','결정'))]
    atomic_failures += ['P0-UT-001: '+marker+' 누락' for marker in ('APPROVED_REQUIREMENT','Assertion Gate') if marker not in ''.join(str(by_test['P0-UT-001'].get(key,'')) for key in ('input','expected','steps','state','success'))]
    record('Phase별 Atomic Assertion 승인 Gate',atomic_failures,
           f'{len(assertions)}개 파싱; 계약 Task만 선착수, 후속 구현은 active REQUIRED/DATA 승인 필요')
    candidate_tasks=[task for task in tasks if task['function'] in active_functions and task['status']=='NOT_STARTED' and not task.get('blocked_by') and all(by_task[dependency]['status']=='DONE' for dependency in task['depends'])]
    context_ready=[task for task in candidate_tasks if task['stage']=='계약' or not pending_by_function.get(task['function'])]
    context_failures=[]
    for task in context_ready:
        pack=ROOT/'작업컨텍스트'/f"{task['id']}.md"
        if not pack.is_file():
            context_failures.append(task['id']+': 작업 컨텍스트 없음')
            continue
        pack_text=pack.read_text(encoding='utf-8')
        context_failures += [task['id']+': '+marker+' 누락' for marker in (task['id'],task['function'],'REQUIRED/DATA Atomic Assertions','Command/Event 계약','권위 문서') if marker not in pack_text]
        if '## 16.' in pack_text:
            context_failures.append(task['id']+': 원문 부록이 작업 컨텍스트에 포함됨')
    record('착수 가능 Task Context Pack',context_failures,
           f'{len(context_ready)}개 ready Task에 계약·Test·Assertion·Schema·Command/Event 요약 제공')
    early_markers=('P2','P3','P6','P8','P12','P14','P17','P23','최초 발견')
    early_failures=['95: '+marker+' 누락' for marker in early_markers[:-1] if marker not in schedule]
    early_failures += ['P23: '+marker+' 누락' for marker in early_markers if marker not in p23_body]
    spike_markers=('save→process kill→recovery','restore 중 kill','storage full','WAL 존재','candidate DB swap 중 kill','손상 generation','save.previous.db')
    early_failures += ['P3 spike: '+marker+' 누락' for marker in spike_markers if marker not in p3_body]
    record('조기 Property/Fuzz/Recovery 검증 소유권',early_failures,
           'P2/P3/P6/P8/P12/P14/P17이 최초 검증, P23은 통합·shrink/repro')
    expected_p0_gate={'P0-UT-001','P0-UT-002','P0-BT-002','P0-BT-003','P0-CT-003','P0-CN-001','P0-CT-004','P0-IT-002'}
    rebuild_status=(ROOT/'검증도구/rebuild_status.py').read_text(encoding='utf-8')
    p0_baseline_failures=[]
    if by_phase[0]['modules'] != 'Gradle root / :app / :core:simulation':
        p0_baseline_failures.append('P0 물리 module 집합 불일치')
    if set(by_phase[0].get('active_function_ids', [])) != {'FUNC-P0-001','FUNC-P0-002'}:
        p0_baseline_failures.append('P0 active_function_ids 불일치')
    if set(by_task['P0-TASK-021']['tests']) != expected_p0_gate or len(by_task['P0-TASK-021']['tests']) != len(expected_p0_gate):
        p0_baseline_failures.append('P0 Gate Test가 정확히 8개가 아님')
    p0_task_plan_lines=task_plan.splitlines()
    for task in (item for item in tasks if item['phase']==0):
        if not any(f'[{task["id"]}](' in line and f'| {task["module"]} |' in line and f'| {task["done"]} |' in line for line in p0_task_plan_lines):
            p0_baseline_failures.append(task['id']+': 전체 Task의 module/완료 기준 불일치')
    if 'C01~C06' in by_phase[0]['gate'] or 'C01~C06' in by_task['P0-TASK-021']['detail']:
        p0_baseline_failures.append('C04~C06이 P0 Gate에 잔존')
    if 'ACTIVE_FUNCTIONS' in rebuild_status:
        p0_baseline_failures.append('rebuild_status.py에 활성 기능 hardcode 잔존')
    record('P0 최소 물리 모듈·활성·Gate 기준선', p0_baseline_failures,
           ':app/:core:simulation, active FUNC-P0-001/002, Gate Test 8개, C01/C02/C03+C14')
    p3_feature=by_func['FUNC-P3-004']
    p25_feature=by_func['FUNC-P25-002']
    phase3_feature=next(x for x in by_phase[3]['features'] if x['id']=='FUNC-P3-004')
    phase25_feature=next(x for x in by_phase[25]['features'] if x['id']=='FUNC-P25-002')
    baseline_sources={
        '마스터': master,
        'P3 상세설계': p3_body,
        'P25 상세설계': p25_body,
        '결정대장': decision_doc,
        'decisions.json': decisions_text,
        'functions.json': ''.join(p3_feature['rules']+p3_feature['normal']+p25_feature['rules']+p25_feature['normal']),
        'phases.json': ''.join(phase3_feature['rules']+phase3_feature['normal']+phase25_feature['rules']+phase25_feature['normal']),
        'tasks.json': ''.join(by_task[test_id]['detail'] for test_id in ('P3-TASK-016','P3-TASK-017','P3-TASK-018','P3-TASK-020','P25-TASK-006','P25-TASK-007','P25-TASK-008','P25-TASK-010')),
        'tests.json': ''.join(str(by_test[test_id].get(key,'')) for test_id in ('P3-UT-004','P3-FT-004','P3-IT-004','P25-UT-002','P25-FT-002','P25-IT-002') for key in ('input','expected','precondition','steps','db','success')),
        '전체 Test': test_plan,
    }
    baseline_failures=[name+': '+marker+' 누락' for name,text in baseline_sources.items() for marker in ('GREENFIELD_V1','LEGACY_CHAIN') if marker not in text]
    obsolete_migration_examples=('schema1fixture에schema2', 'schema1세이브→앱schema3', 'schema3세이브를schema2앱', 'migration2→3')
    baseline_failures += [name+': 가상 migration 예제 잔존 '+pattern for name,text in baseline_sources.items() for pattern in obsolete_migration_examples if pattern in re.sub(r'\s+','',text)]
    record('실제 증거 기반 SchemaBaselineMode', baseline_failures,
           'GREENFIELD_V1은 최초 v1·migration 0개; LEGACY_CHAIN은 실제 exported schema/DB fixture만 사용')
    critical_test_markers={
        'P0-UT-001': ('sourceHash', 'decisionId', 'APPROVED_REQUIREMENT'),
        'P0-UT-002': ('javaVersion', 'Gradle'),
        'P0-BT-002': ('forbiddenSymbol', 'Gradle'),
        'P0-BT-003': ('boundary', 'JUnit'),
        'P0-CT-003': ('codecId', 'golden'),
        'P0-CN-001': ('submissionSequence', 'capacity 64'),
        'P0-CT-004': ('semantics', '48dp'),
        'P0-IT-002': ('applicationId', 'Room schema'),
        'P3-FT-001': ('BEFORE_RNG_WRITE', '새 connection'),
        'P3-IT-004': ('sourceSchemaHash', ':core:save'),
        'P6-IT-007': ('WorldSession.execute', '같은 commandId'),
        'P22-CT-004': ('lifecycle', 'effectId'),
        'P25-IT-002': ('EventCodecId', 'projectionHash'),
    }
    critical_failures=[]
    for test_id, markers in critical_test_markers.items():
        critical_failures += [test_id+': '+failure for failure in test_detail_failures(by_test[test_id], markers)]
    record('Gate 핵심 Test 실행 가능성', critical_failures,
           '범용 template 금지; 실제 fixture/조립/절차/독립 DB·로그 oracle와 증거 marker 요구')
    public_snapshot_sources={
        'Command/Event 계약': contract,
        'P22 상세설계': p22_body,
        'functions.json': ''.join(by_func['FUNC-P22-004']['rules']),
        'phases.json': ''.join(next(x for x in by_phase[22]['features'] if x['id']=='FUNC-P22-004')['rules']),
        'tasks.json': by_task['P22-TASK-017']['detail']+by_task['P22-TASK-017']['done'],
        'tests.json': by_test['P22-CT-004']['input']+by_test['P22-CT-004']['expected'],
        '전체 Test': test_plan,
    }
    public_markers=('PublicSnapshot', 'sessionEpoch', 'stateVersion')
    record('PublicSnapshot epoch·version 순서 계약',
           [name+': '+marker+' 누락' for name,text in public_snapshot_sources.items() for marker in public_markers if marker not in text],
           '현재 epoch만 수락, stateVersion 단조/동일 멱등, generationId durable 출처')
    validator_self_checks()
    failures=[]; children=defaultdict(list); indegree={t['id']:len(t['depends']) for t in tasks}
    for t in tasks:
        if t['id'] in t['depends']: failures.append(t['id']+': self dependency')
        for dep in t['depends']:
            if dep not in by_task: failures.append(t['id']+': '+dep)
            children[dep].append(t['id'])
        failures += [t['id']+': '+x for x in t['tests'] if x not in by_test]
    ready=deque(k for k,v in indegree.items() if not v); order=[]
    while ready:
        x=ready.popleft();order.append(x)
        for child in children[x]:
            indegree[child]-=1
            if indegree[child]==0:ready.append(child)
    if len(order)!=len(tasks):failures.append('dependency cycle or missing parent')
    for t in tasks:
        if set(t.get('successors',[]))!=set(children[t['id']]): failures.append(t['id']+': successor mismatch')
    record('Task DAG·선행/후속 일치',failures, f'{len(order)}개 Task 위상 정렬')
    record('Phase DAG', [f'P{p["n"]} <- P{d}' for p in phases for d in p['deps'] if d not in by_phase or d>=p['n']], '모든 선행 Phase가 실제 존재하며 현재 순서가 DAG를 만족')
    failures=[]
    for p in phases:
        body=(ROOT/p['file']).read_text(encoding='utf-8')
        for number in range(1,17):
            if not re.search(rf'^## {number}\. ',body,re.M): failures.append(p['file']+f': section {number} missing')
        for id in p['task_ids']+p['test_ids']:
            if f'id="{id.lower()}"' not in body: failures.append(p['file']+': '+id+' missing')
    record('Phase 공통 목차·독립 Task/Test 본문',failures, '26개 문서 × 16개 기본 섹션 및 모든 소유 Task/Test 본문 확인')
    # Validate authored links, not unmodified references and example links in original material.
    failures=[]; link_count=0
    for path in ROOT.glob('*.md'):
        text=path.read_text(encoding='utf-8')
        authored=re.split(r'^## 16\. ',text, maxsplit=1, flags=re.M)[0] if 'Phase' in path.name else text
        for url in re.findall(r'\[[^\]\n]*\]\(([^)\n]+)\)',unfenced(authored)):
            if re.match(r'^(https?://|mailto:)',url): continue
            relative,_,anchor=unquote(url).partition('#')
            target=(path.parent/relative) if relative else path
            link_count+=1
            if not target.is_file(): failures.append(path.name+': missing '+url);continue
            if anchor:
                target_text=target.read_text(encoding='utf-8')
                # All generated anchors are explicit HTML IDs; heading slugs need no heuristics.
                if not re.search(r'id=[\"\']'+re.escape(anchor)+r'[\"\']',target_text):failures.append(path.name+': missing anchor '+url)
    record('로컬 Markdown 링크·명시적 anchor',failures,f'{link_count}개 링크 확인; 코드 블록 및 원문 부록 링크는 제외')
    required_fields=['precondition','input','steps','expected','db','logs','state','success']
    record('Test Case 필수 검증 필드',[t['id']+': '+k for t in tests for k in required_fields if not t.get(k)], f'{len(tests)}개 Case의 사전조건·입력·절차·기대·DB·로그·상태·성공조건 검사')
    compact=lambda value: re.sub(r'[\s`]','',str(value))
    sync_failures=[]
    phase_bodies={p['n']:(ROOT/p['file']).read_text(encoding='utf-8') for p in phases}
    for test in tests:
        if test.get('phase') not in phase_bodies:
            continue
        marker=f'<a id="{test["id"].lower()}"></a>'
        body=phase_bodies[test['phase']]
        if marker not in body:
            sync_failures.append(test['id']+': Phase anchor missing')
            continue
        block=body.split(marker,1)[1].split('<a id=',1)[0]
        for key in (*required_fields,'status'):
            if compact(test[key]) not in compact(block):
                sync_failures.append(test['id']+f': Phase {key} mismatch')
    plan=(ROOT/'91_전체_Test_계획서.md').read_text(encoding='utf-8')
    plan_rows={}
    plan_counts=Counter()
    for line in plan.splitlines():
        match=re.match(r'^\| \[([A-Z0-9-]+)\]\([^)]+\) \| ([^|]+) \| ([^|]+) \| (.*?) \| (.*?) \| ([A-Z_]+) \|$',line)
        if match:
            plan_counts[match.group(1)]+=1
            plan_rows[match.group(1)]=match.groups()[1:]
    for test in tests:
        row=plan_rows.get(test['id'])
        if row is None:
            marker=f'<a id="{test["id"].lower()}"></a>'
            if marker not in plan:
                sync_failures.append(test['id']+': global plan row/block missing')
                continue
            block=plan.split(marker,1)[1].split('<a id=',1)[0]
            for key in ('type','function','input','expected','status'):
                if compact(test[key]) not in compact(block):
                    sync_failures.append(test['id']+f': global plan block {key} mismatch')
            continue
        actual_type,actual_function,actual_input,actual_expected,actual_status=row
        expected_values=(test['type'],test['function'],test['input'],test['expected'],test['status'])
        actual_values=(actual_type,actual_function,actual_input,actual_expected,actual_status)
        if tuple(map(compact,actual_values))!=tuple(map(compact,expected_values)):
            sync_failures.append(test['id']+': global plan row mismatch')
    sync_failures += [item+': duplicate global plan row' for item,count in plan_counts.items() if count>1]
    evidence=(ROOT/'검증증거/2026-09-10_Phase0_Gate_실행증거.md').read_text(encoding='utf-8')
    tests_by_id={test['id']:test for test in tests}
    for pattern,test_id,label,unit in (
        (r'`WorldSessionTest` (\d+)건','P0-UT-002','WorldSessionTest','건'),
        (r'fixture (\d+)개','P0-BT-002','fixture','개')
    ):
        match=re.search(pattern,evidence)
        if not match:
            sync_failures.append(test_id+': evidence count missing')
            continue
        expected=f'{label} {match.group(1)}{unit}'
        if expected not in tests_by_id[test_id].get('actual',''):
            sync_failures.append(test_id+': management evidence count mismatch')
        if expected not in phase_bodies[0]:
            sync_failures.append(test_id+': Phase evidence count mismatch')
    record('Test 관리데이터→Phase·전역 계획 동기화',sync_failures,f'{len(tests)}개 Test의 사전조건·입력·절차·기대·DB·로그·상태·성공조건·실행상태 비교')
    record('테스트 상태에 실행증거 요구',[t['id'] for t in tests if t['status']=='PASS' and not t.get('evidence')], f'현재 {Counter(t["status"] for t in tests)}; 문서 검사 결과를 게임 Test에 전파하지 않음')
    sql_checks()
    counts=Counter(x['status'] for x in RESULTS)
    now=datetime.now(timezone(timedelta(hours=9))).isoformat(timespec='seconds')
    report={'executed_at':now,'scope':'documentation_and_proposed_sql_only','checks':RESULTS,'summary':dict(counts),'game_test_execution':'NOT_RUN'}
    (ROOT/'관리데이터/document_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# 97. 문서 정합성 검증보고서','',f'> 실제 검사시각: {now} · 검증도구: `검증도구/validate_docs.py`','',
           f'문서·제안 SQL 검사 **{len(RESULTS)}개 그룹**: PASS **{counts["PASS"]}**, FAIL **{counts["FAIL"]}**.','',
           '**이 보고서는 게임 코드의 Unit/Integration/E2E 테스트 보고서가 아니다.** 게임 테스트 751개는 계획이며 아직 NOT_RUN이다. 실제로 실행한 것은 문서 구조·ID·참조·DAG 검사와 메모리 SQLite에 대한 제안 DDL/제약조건 smoke 검사다.','',
           '## 1. 실행 결과','','| 검사 | 결과 | 검사 범위 |','|---|---|---|']
    for result in RESULTS:
        lines.append(f'| {result["name"]} | {result["status"]} | {str(result["detail"]).replace("|","/")} |')
    lines+=['','## 2. 오류 상세','']
    errors=[f'{r["name"]}: {e}' for r in RESULTS for e in r['failures']]
    lines+=['- '+e for e in errors] or ['검사 범위 내 구조 오류는 발견되지 않았다.']
    lines+=['','## 3. 별도로 남아 있는 검토와 실증','','| 항목 | 현재 상태 | 완료 증거 |','|---|---|---|',
            '| 3,135개 절의 모든 하위 조항 수용성 | 절 묶음 매핑 완료, 의미 검토 필요 | 기능별 assertion manifest·프로필/데이터행·시험 연결 리뷰 |',
            '| 실제 Android 코드 / Room3·KSP 컴파일 | 코드 미제공, 미실행 | 재현 가능한 저장소·잠금버전·빌드 로그 |',
            '| 751개 게임 Test 실행 | NOT_RUN | 실제 Unit/Component/Integration/Android/E2E 결과 |',
            '| 실제 10,000개 초상 및 필수 자산 | 미제공 | 파일 존재·decode·체크섬·배포 용량 검사 |',
            '| 게임 밸런스·장기 100/300년 시뮬레이션 | 미실행 | 고정 Seed·불변식·통계·수용성 결과 |',
            '| 제안 SQL 실제 마이그레이션·락/부하·기기 복구 | 미실행 | 실제 Room 생성 스키마와 구버전 DB·장애주입 결과 |',
            '| 미승인 설계 보완안 | 결정대장에 보존 | 결정권자·승인일·영향 Task·회귀 증거 |',
            '| 개발 일정 | 초기 공수 모델 | 담당자·착수일·병렬 용량·실측 속도·리스크 반영 |','',
            'SQL smoke는 SQLite의 제약조건과 최소 원자성 예를 검증한다. 전체 SaveManager, 예약 엔진, 전투 엔진, 실제 롤백 제품 경로가 구현되어 작동한다는 주장은 하지 않는다.','',
            '## 4. 재실행','','```text','py -3 검증도구/rebuild_status.py','py -3 검증도구/validate_docs.py','```','',
            'FAIL이면 프로세스 종료코드 1을 반환한다. Windows에서도 UTF-8 지원 Python 3.10 이상으로 실행할 수 있으며 외부 패키지는 필요 없다.','',
            '[마스터](00_전체_구현_마스터_설계서.md) · [진행현황](98_진행현황_대시보드.md) · [추적표](93_요구사항_추적표.md)','']
    (ROOT/'97_문서정합성_검증보고서.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps({'checks':len(RESULTS),**dict(counts),'game_tests':'NOT_RUN'},ensure_ascii=False))
    if errors:
        print('\n'.join(errors[:40]))
    return int(bool(counts['FAIL']))

if __name__=='__main__':
    raise SystemExit(main())
