#!/usr/bin/env python3
"""检查不可变历史 schema、源码冲突标记和本仓库 AI 入口。"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / 'app/schemas/me.kafuuneko.rpclient.libs.room.AppDatabase'


def main():
    errors = []
    expected = json.loads((ROOT / 'scripts/historical-schema-hashes.json').read_text())
    for version, digest in expected.items():
        path = SCHEMAS / f'{version}.json'
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            errors.append(f'Historical fork schema changed: {path.relative_to(ROOT)}')
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    for name in tracked:
        path = ROOT / name
        if not path.is_file() or path.suffix not in {'.kt', '.kts', '.xml', '.json', '.yml', '.md'}:
            continue
        text = path.read_text(encoding='utf-8')
        if re.search(r'^(?:<{7} |>{7} |={7}$)', text, re.MULTILINE):
            errors.append(f'Unresolved merge marker: {name}')
    for name in ('README_ZH.md', 'doc/coding-guidelines.md', 'doc/build-and-verification.md', 'doc/upstream-integration.md'):
        if not (ROOT / name).is_file():
            errors.append(f'Missing entry document: {name}')
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f'Repository checks passed; {len(expected)} historical fork schemas unchanged.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
