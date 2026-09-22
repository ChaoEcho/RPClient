#!/usr/bin/env python3
"""在无 Android SDK 的主机验证 fork v9→v10 SQL 与配图引用不变量；不能替代 Room 验收。"""
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parent.parent


def statements(source):
    pattern = r'db\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\)'
    return [multiline or line for multiline, line in re.findall(pattern, source, re.S)]


def main():
    schema = json.loads((ROOT / 'app/schemas/me.kafuuneko.rpclient.libs.room.AppDatabase/9.json').read_text())['database']
    with sqlite3.connect(':memory:') as db:
        db.execute('PRAGMA foreign_keys=ON')
        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))

        def insert(table, overrides):
            entity = next(item for item in schema['entities'] if item['tableName'] == table)
            values = {
                field['columnName']: '' if field['affinity'] == 'TEXT' else 0
                for field in entity['fields'] if field.get('notNull', False) and 'defaultValue' not in field
            }
            values.update(overrides)
            columns = ','.join(f'`{key}`' for key in values)
            placeholders = ','.join('?' for _ in values)
            db.execute(f'INSERT INTO `{table}` ({columns}) VALUES ({placeholders})', tuple(values.values()))

        insert('character', {'id': 101, 'name': 'fixture', 'avatar': 'image-old'})
        insert('chat_sessions', {'id': 202, 'characterId': 101})
        insert('files', {'uuid': 'image-old', 'hash': 'a' * 64, 'mimeType': 'image/png'})
        for message_id in (303, 304):
            insert('chat_messages', {'id': message_id, 'sessionId': 202, 'source': 'Char',
                                    'content': 'reply', 'imageFileUuid': 'image-old'})
        source = (ROOT / 'app/src/main/java/me/kafuuneko/rpclient/libs/room/migration/Migration9To10.kt').read_text()
        for statement in statements(source):
            db.execute(statement)
        assert db.execute('SELECT imageFileUuid FROM chat_messages').fetchall() == [(None,), (None,)]
        assert db.execute('SELECT messageType,messageId,sendToModel FROM message_images ORDER BY messageId').fetchall() == [
            ('single', 303, 0), ('single', 304, 0)
        ]
        assert db.execute('SELECT COUNT(DISTINCT imageUuid) FROM message_images').fetchone() == (2,)
        assert db.execute("SELECT hash FROM files WHERE uuid='image-old'").fetchone() == ('a' * 64,)
        assert not db.execute('PRAGMA foreign_key_check').fetchall()

        current = json.loads((ROOT / 'app/schemas/me.kafuuneko.rpclient.libs.room.AppDatabase/10.json').read_text())['database']
        for entity in current['entities']:
            table = entity['tableName']
            actual = {row[1]: (row[2].upper(), bool(row[3]), row[4])
                      for row in db.execute(f'PRAGMA table_info(`{table}`)')}
            expected = {field['columnName']: (field['affinity'], field.get('notNull', False), field.get('defaultValue'))
                        for field in entity['fields']}
            assert actual == expected, f'Migration columns differ from Room v10: {table}'
            for index in entity.get('indices', []):
                name = index['name']
                columns = [row[2] for row in db.execute(f'PRAGMA index_info(`{name}`)')]
                assert columns == index['columnNames'], f'Migration index differs from Room v10: {name}'
        for statement in statements(source.split('internal fun migrateLegacyGeneratedImages', 1)[1]):
            db.execute(statement)
        assert db.execute('SELECT COUNT(*) FROM message_images').fetchone() == (2,)
    print('SQLite v9→v10 fixture passed: display-only, independent references, avatar retention and idempotence.')


if __name__ == '__main__':
    main()
