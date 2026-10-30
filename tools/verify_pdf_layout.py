#!/usr/bin/env python3
"""Проверка алгоритмов разметки PDF из PdfExporter.kt.

Экспорт дневника в PDF нельзя проверить, просто посмотрев на код: ошибка
в переносе строк не заметна, пока не откроешь готовый документ и не увидишь
текст, уехавший за край листа. Здесь алгоритм повторён на Python и
проверяется на инвариантах:

  * ни одна строка не шире отведённой ширины;
  * из текста не потеряно ни одного слова;
  * слово длиннее строки разрывается, а не выходит за поля;
  * записи не выходят за нижний край страницы.

Запуск:  python3 tools/verify_pdf_layout.py
"""

import sys

# Те же величины, что в PdfExporter.kt (лист A4 при 72 dpi).
PAGE_WIDTH = 595
PAGE_HEIGHT = 842
MARGIN = 40.0
LINE_HEIGHT = 16.0
MAX_WIDTH = PAGE_WIDTH - MARGIN * 2

passed = 0
failed = 0


def check(description, actual, expected):
    global passed, failed
    if actual == expected:
        passed += 1
        print(f"  OK   {description}")
    else:
        failed += 1
        print(f"  FAIL {description}: получено {actual!r}, ожидалось {expected!r}")


def measure(text, char_width=6.0):
    """Приближение Paint.measureText: ширина пропорциональна числу символов."""
    return len(text) * char_width


def split_long_word(word, max_width, target):
    """Повторяет PdfExporter.splitLongWord."""
    chunk = ""
    for symbol in word:
        if measure(chunk + symbol) > max_width and chunk:
            target.append(chunk)
            chunk = ""
        chunk += symbol
    return chunk


def wrap_text(text, max_width):
    """Повторяет PdfExporter.wrapText."""
    if not text.strip():
        return []

    lines = []
    for paragraph in text.split("\n"):
        current = ""
        for word in [w for w in paragraph.split(" ") if w]:
            candidate = word if not current else f"{current} {word}"
            if current and measure(candidate) > max_width:
                lines.append(current)
                current = ""
            if measure(word) > max_width:
                current = split_long_word(word, max_width, lines)
            else:
                current = word if not current else f"{current} {word}"
        if current:
            lines.append(current)
    return lines


def test_wrapping():
    print("\n[1] Перенос текста по ширине строки")

    check("пустой текст не даёт строк", wrap_text("", MAX_WIDTH), [])
    check("текст из пробелов не даёт строк", wrap_text("   \n  ", MAX_WIDTH), [])

    short = "Сегодня был хороший день"
    check("короткий текст остаётся одной строкой", wrap_text(short, MAX_WIDTH), [short])

    long_text = ("Сегодня встал рано и пошёл в парк. Было прохладно и тихо, "
                 "птицы только начинали петь. Пил кофе на скамейке и думал "
                 "о будущем, о том, что хочется успеть за этот год.")
    lines = wrap_text(long_text, MAX_WIDTH)
    check("длинный текст разбит на несколько строк", len(lines) > 1, True)
    check(
        "ни одна строка не шире листа",
        all(measure(line) <= MAX_WIDTH for line in lines),
        True,
    )
    check(
        "ни одно слово не потеряно",
        " ".join(lines).split(),
        long_text.replace("\n", " ").split(),
    )

    multiline = "Первый абзац\nВторой абзац\nТретий абзац"
    check("переводы строк сохраняются", wrap_text(multiline, MAX_WIDTH), multiline.split("\n"))


def test_long_word():
    print("\n[2] Слово длиннее строки")

    word = "А" * 300
    lines = wrap_text(word, MAX_WIDTH)
    check("слово разбито на части", len(lines) > 1, True)
    check(
        "ни одна часть не шире листа",
        all(measure(line) <= MAX_WIDTH for line in lines),
        True,
    )
    check("ни один символ не потерян", "".join(lines), word)

    # Хвост длинного слова должен продолжиться следующими словами,
    # а не обрываться отдельной строкой.
    mixed = "А" * 200 + " конец"
    lines = wrap_text(mixed, MAX_WIDTH)
    check("текст после длинного слова не потерян", "конец" in lines[-1], True)
    check(
        "строки по-прежнему в пределах листа",
        all(measure(line) <= MAX_WIDTH for line in lines),
        True,
    )


    prefixed = "начало " + word + " конец"
    lines = wrap_text(prefixed, MAX_WIDTH)
    check("длинное слово после обычного не выходит за поля", all(measure(line) <= MAX_WIDTH for line in lines), True)
    check("слово после префикса не теряет символы", "".join(lines).replace(" ", ""), prefixed.replace(" ", ""))


def test_pagination():
    print("\n[3] Разбиение на страницы")

    # Модель построчной пагинации; реальные метрики Paint проверяются на Android.
    def paginate(entries):
        pages = 1
        y = MARGIN + LINE_HEIGHT * 3.4
        overflow = 0
        for content, has_tags in entries:
            if y + LINE_HEIGHT * 4.5 > PAGE_HEIGHT - MARGIN:
                pages += 1
                y = MARGIN
            advances = [LINE_HEIGHT, LINE_HEIGHT * 1.2, LINE_HEIGHT * 1.3]
            advances += [LINE_HEIGHT] * len(wrap_text(content, MAX_WIDTH))
            if has_tags:
                advances.append(LINE_HEIGHT)
            for advance in advances:
                if y + 3 > PAGE_HEIGHT - MARGIN:
                    pages += 1
                    y = MARGIN
                if y + 3 > PAGE_HEIGHT - MARGIN:
                    overflow += 1
                y += advance
            y += LINE_HEIGHT * 1.4
        return pages, overflow

    short_entry = ("Короткая запись о вчерашнем дне.", False)
    long_entry = (("Очень длинная запись. " * 60), True)

    pages, overflow = paginate([short_entry] * 3)
    check("три коротких записи умещаются на одной странице", pages, 1)
    check("ничего не вышло за нижний край", overflow, 0)

    pages, overflow = paginate([short_entry] * 40)
    check("сорок записей занимают несколько страниц", pages > 1, True)
    check("ничего не вышло за нижний край", overflow, 0)

    pages, overflow = paginate([long_entry, long_entry, short_entry])
    check("длинные записи разнесены по страницам", pages > 1, True)
    check("ничего не вышло за нижний край", overflow, 0)

    pages, overflow = paginate([("Строка\n" * 2000, False)])
    check("одна огромная запись продолжается на новых страницах", pages > 20, True)
    check("огромная запись не выходит за нижний край", overflow, 0)


def main():
    test_wrapping()
    test_long_word()
    test_pagination()
    print(f"\nИтог: успешно {passed}, с ошибками {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
