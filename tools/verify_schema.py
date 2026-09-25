#!/usr/bin/env python3
"""Проверка схемы базы данных приложения «Дневник».

Скрипт поднимает базу SQLite в памяти, применяет к ней tools/schema.sql,
заполняет её тестовыми данными и проверяет, что структура действительно
соответствует требованиям технического задания:

  * есть три основные таблицы и одна промежуточная;
  * работает связь «один-ко-многим» (категория → записи);
  * работает связь «многие-ко-многим» (записи ↔ теги);
  * внешние ключи действительно каскадно удаляют связанные строки;
  * полнотекстовый поиск находит записи по словам из текста;
  * запросы статистики возвращают корректные агрегаты.

Запуск:  python3 tools/verify_schema.py
"""

import os
import sqlite3
import sys
from datetime import datetime, timedelta

SCHEMA_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema.sql")

# Счётчики результатов проверок.
passed = 0
failed = 0


def check(description, actual, expected):
    """Сравнивает фактический результат с ожидаемым и печатает статус."""
    global passed, failed
    if actual == expected:
        passed += 1
        print(f"  OK   {description}")
    else:
        failed += 1
        print(f"  FAIL {description}: получено {actual!r}, ожидалось {expected!r}")


def millis(days_ago):
    """Возвращает Unix-время в миллисекундах для даты «N дней назад»."""
    return int((datetime(2026, 9, 9) - timedelta(days=days_ago)).timestamp() * 1000)


def build_database():
    """Создаёт базу в памяти и применяет к ней схему из schema.sql."""
    connection = sqlite3.connect(":memory:")
    connection.executescript(open(SCHEMA_PATH, encoding="utf-8").read())
    # executescript открывает свою транзакцию и сбрасывает PRAGMA foreign_keys,
    # поэтому включаем внешние ключи ещё раз — уже на уровне соединения.
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def seed(connection):
    """Заполняет базу тестовыми данными: 3 категории, 3 тега, 5 записей."""
    now = millis(0)

    categories = [("Личное", "#8B7CF6"), ("Работа", "#4DD0A5"), ("Здоровье", "#FF8A65")]
    for name, color in categories:
        connection.execute(
            "INSERT INTO categories (name, color, icon, is_default, created_at) "
            "VALUES (?, ?, 'ic_category', 1, ?)",
            (name, color, now),
        )

    for name in ("отпуск", "спорт", "семья"):
        connection.execute(
            "INSERT INTO tags (name, color, created_at) VALUES (?, '#4DD0A5', ?)",
            (name, now),
        )

    entries = [
        ("Отличное утро в парке", "Сегодня встал рано и пошёл в парк. Пил кофе на скамейке.", 0, "GREAT", 1),
        ("Сложный день на работе", "Много задач и совещаний, устал к вечеру.", 1, "SAD", 2),
        ("Пробежка пять километров", "Первая длинная пробежка за месяц, чувствую себя бодро.", 2, "GOOD", 3),
        ("Тихий вечер дома", "Читал книгу и слушал дождь за окном.", 3, "NEUTRAL", 1),
        ("Планы на отпуск", "Выбирали маршрут поездки всей семьёй.", 4, "GREAT", 1),
    ]
    for title, content, days_ago, mood, category_id in entries:
        connection.execute(
            "INSERT INTO entries (title, content, entry_date, mood, category_id, "
            "is_favorite, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
            (title, content, millis(days_ago), mood, category_id, now, now),
        )

    # Связи «многие-ко-многим»: запись 1 → теги «отпуск» и «семья»,
    # запись 3 → тег «спорт», запись 5 → теги «отпуск» и «семья».
    links = [(1, 1), (1, 3), (3, 2), (5, 1), (5, 3)]
    connection.executemany(
        "INSERT INTO entry_tags (entry_id, tag_id) VALUES (?, ?)", links
    )
    connection.commit()


def test_structure(connection):
    """Проверяет, что созданы все требуемые таблицы."""
    print("\n[1] Структура базы данных")
    tables = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }
    for table in ("categories", "entries", "tags", "entry_tags"):
        check(f"таблица {table} существует", table in tables, True)
    check("виртуальная таблица entries_fts существует", "entries_fts" in tables, True)

    main_tables = {"categories", "entries", "tags"}
    check("основных таблиц не меньше трёх", len(main_tables & tables), 3)


def test_foreign_keys(connection):
    """Проверяет, что внешние ключи объявлены и включены."""
    print("\n[2] Внешние ключи")
    check(
        "режим foreign_keys включён",
        connection.execute("PRAGMA foreign_keys").fetchone()[0],
        1,
    )

    entry_fk = connection.execute("PRAGMA foreign_key_list(entries)").fetchall()
    check("у entries один внешний ключ", len(entry_fk), 1)
    check("entries.category_id ссылается на categories", entry_fk[0][2], "categories")
    check("удаление категории каскадное", entry_fk[0][6], "CASCADE")

    link_fk = connection.execute("PRAGMA foreign_key_list(entry_tags)").fetchall()
    check("у entry_tags два внешних ключа", len(link_fk), 2)
    check(
        "оба внешних ключа entry_tags каскадные",
        {fk[6] for fk in link_fk},
        {"CASCADE"},
    )

    # Ссылка на несуществующую категорию должна отклоняться базой.
    try:
        connection.execute(
            "INSERT INTO entries (title, content, entry_date, mood, category_id, "
            "created_at, updated_at) VALUES ('x', '', 0, 'GOOD', 999, 0, 0)"
        )
        connection.rollback()
        check("вставка с несуществующей категорией отклонена", False, True)
    except sqlite3.IntegrityError:
        check("вставка с несуществующей категорией отклонена", True, True)


def test_one_to_many(connection):
    """Проверяет связь «одна категория → много записей»."""
    print("\n[3] Связь «один-ко-многим»")
    rows = connection.execute(
        "SELECT c.name, COUNT(e.id) FROM categories c "
        "LEFT JOIN entries e ON e.category_id = c.id "
        "GROUP BY c.id ORDER BY c.id"
    ).fetchall()
    check("записей в категории «Личное»", dict(rows)["Личное"], 3)
    check("записей в категории «Работа»", dict(rows)["Работа"], 1)
    check("записей в категории «Здоровье»", dict(rows)["Здоровье"], 1)


def test_many_to_many(connection):
    """Проверяет связь «записи ↔ теги» через промежуточную таблицу."""
    print("\n[4] Связь «многие-ко-многим»")
    tags_of_first = [
        row[0]
        for row in connection.execute(
            "SELECT t.name FROM tags t "
            "JOIN entry_tags et ON et.tag_id = t.id "
            "WHERE et.entry_id = 1 ORDER BY t.name"
        )
    ]
    check("у первой записи два тега", tags_of_first, ["отпуск", "семья"])

    entries_of_tag = connection.execute(
        "SELECT COUNT(*) FROM entry_tags et "
        "JOIN tags t ON t.id = et.tag_id WHERE t.name = 'отпуск'"
    ).fetchone()[0]
    check("тег «отпуск» стоит у двух записей", entries_of_tag, 2)

    # Составной первичный ключ не должен допускать дубликат связи.
    try:
        connection.execute("INSERT INTO entry_tags (entry_id, tag_id) VALUES (1, 1)")
        connection.rollback()
        check("повторная связь запись–тег отклонена", False, True)
    except sqlite3.IntegrityError:
        check("повторная связь запись–тег отклонена", True, True)


def test_cascade(connection):
    """Проверяет каскадное удаление по требованию ТЗ."""
    print("\n[5] Каскадное удаление")
    connection.execute("DELETE FROM entries WHERE id = 1")
    check(
        "связи удалённой записи убраны из entry_tags",
        connection.execute(
            "SELECT COUNT(*) FROM entry_tags WHERE entry_id = 1"
        ).fetchone()[0],
        0,
    )

    connection.execute("DELETE FROM categories WHERE name = 'Личное'")
    check(
        "записи удалённой категории удалены",
        connection.execute(
            "SELECT COUNT(*) FROM entries WHERE category_id = 1"
        ).fetchone()[0],
        0,
    )
    check(
        "связи этих записей тоже удалены",
        connection.execute(
            "SELECT COUNT(*) FROM entry_tags WHERE entry_id IN (4, 5)"
        ).fetchone()[0],
        0,
    )
    connection.rollback()  # возвращаем данные для остальных проверок


def test_full_text_search(connection):
    """Проверяет изюминку проекта — полнотекстовый поиск."""
    print("\n[6] Полнотекстовый поиск (FTS4)")
    found = [
        row[0]
        for row in connection.execute(
            "SELECT e.title FROM entries e "
            "JOIN entries_fts f ON f.docid = e.id "
            "WHERE entries_fts MATCH ? ORDER BY e.id",
            ("парк*",),
        )
    ]
    check("поиск по слову «парк» находит запись", found, ["Отличное утро в парке"])

    found = [
        row[0]
        for row in connection.execute(
            "SELECT e.id FROM entries e JOIN entries_fts f ON f.docid = e.id "
            "WHERE entries_fts MATCH ?",
            ("отпуск*",),
        )
    ]
    check("поиск по слову «отпуск» находит одну запись", found, [5])

    # Индекс должен обновляться при изменении записи (триггеры синхронизации).
    connection.execute(
        "UPDATE entries SET content = 'Совершенно новый текст про велосипед' WHERE id = 2"
    )
    found = [
        row[0]
        for row in connection.execute(
            "SELECT e.id FROM entries e JOIN entries_fts f ON f.docid = e.id "
            "WHERE entries_fts MATCH ?",
            ("велосипед",),
        )
    ]
    check("индекс обновился после UPDATE", found, [2])
    found = [
        row[0]
        for row in connection.execute(
            "SELECT e.id FROM entries e JOIN entries_fts f ON f.docid = e.id "
            "WHERE entries_fts MATCH ?",
            ("совещаний",),
        )
    ]
    check("старый текст пропал из индекса", found, [])

    # Индекс должен очищаться при удалении записи.
    connection.execute("DELETE FROM entries WHERE id = 2")
    found = connection.execute(
        "SELECT COUNT(*) FROM entries_fts WHERE entries_fts MATCH ?", ("велосипед",)
    ).fetchone()[0]
    check("индекс очистился после DELETE", found, 0)
    connection.rollback()


def test_statistics(connection):
    """Проверяет запросы, на которых строится экран статистики."""
    print("\n[7] Запросы статистики")
    check(
        "всего записей",
        connection.execute("SELECT COUNT(*) FROM entries").fetchone()[0],
        5,
    )

    top_mood = connection.execute(
        "SELECT mood, COUNT(*) c FROM entries GROUP BY mood ORDER BY c DESC LIMIT 1"
    ).fetchone()
    check("самое частое настроение", top_mood, ("GREAT", 2))

    by_category = connection.execute(
        "SELECT c.name, COUNT(e.id) c FROM categories c "
        "JOIN entries e ON e.category_id = c.id "
        "GROUP BY c.id ORDER BY c DESC, c.name"
    ).fetchall()
    check("распределение по категориям", by_category[0], ("Личное", 3))

    week_start = millis(6)
    check(
        "записей за последние 7 дней",
        connection.execute(
            "SELECT COUNT(*) FROM entries WHERE entry_date >= ?", (week_start,)
        ).fetchone()[0],
        5,
    )


def main():
    connection = build_database()
    seed(connection)

    test_structure(connection)
    test_foreign_keys(connection)
    test_one_to_many(connection)
    test_many_to_many(connection)
    test_cascade(connection)
    test_full_text_search(connection)
    test_statistics(connection)

    print(f"\nИтог: успешно {passed}, с ошибками {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
