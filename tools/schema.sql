-- ============================================================================
--  Приложение «Дневник» — схема базы данных (SQLite)
--  ---------------------------------------------------------------------------
--  Этот скрипт описывает ту же структуру, которую Room создаёт из аннотаций
--  в пакете data/database/entity. Он нужен для двух вещей:
--    1) документирование схемы в читаемом виде;
--    2) автоматическая проверка схемы скриптом tools/verify_schema.py.
--
--  Таблицы:
--    categories  — основная таблица №1 (категории записей)
--    entries     — основная таблица №2 (записи дневника)
--    tags        — основная таблица №3 (теги)
--    entry_tags  — промежуточная таблица (связь «многие-ко-многим»)
--    entries_fts — виртуальная таблица FTS4 для полнотекстового поиска
-- ============================================================================

-- Внешние ключи в SQLite по умолчанию выключены, их нужно включать явно.
-- В приложении это делает Room (см. AppDatabase.onOpen).
PRAGMA foreign_keys = ON;

-- ----------------------------------------------------------------------------
-- 1. Категории. Родительская сторона связи «один-ко-многим».
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,                        -- «Личное», «Работа»…
    color      TEXT    NOT NULL DEFAULT '#8B7CF6',      -- цвет чипа, формат #RRGGBB
    icon       TEXT    NOT NULL DEFAULT 'ic_category',  -- имя ресурса иконки
    is_default INTEGER NOT NULL DEFAULT 0,              -- 1 — предустановленная категория
    created_at INTEGER NOT NULL                         -- Unix-время в миллисекундах
);

-- Имя категории уникально: нельзя завести две категории «Работа».
CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name);

-- ----------------------------------------------------------------------------
-- 2. Записи дневника. Главная сущность приложения.
--    Дочерняя сторона связи «один-ко-многим» с categories.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entries (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    title       TEXT    NOT NULL,                 -- заголовок записи
    content     TEXT    NOT NULL DEFAULT '',      -- текст записи
    entry_date  INTEGER NOT NULL,                 -- дата события, Unix-время в мс
    mood        TEXT    NOT NULL,                 -- GREAT | GOOD | NEUTRAL | SAD | ANGRY
    category_id INTEGER NOT NULL,                 -- внешний ключ на categories
    is_favorite INTEGER NOT NULL DEFAULT 0,       -- признак «избранное»
    created_at  INTEGER NOT NULL,                 -- когда запись создана
    updated_at  INTEGER NOT NULL,                 -- когда изменена в последний раз

    -- Требование ТЗ: «При удалении категории удаляются все связанные записи».
    FOREIGN KEY (category_id) REFERENCES categories (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

-- Индекс по внешнему ключу обязателен: без него SQLite делает полный перебор
-- таблицы при каждом каскадном удалении категории.
CREATE INDEX IF NOT EXISTS index_entries_category_id ON entries (category_id);
-- Индексы под сортировку по дате и фильтрацию по настроению.
CREATE INDEX IF NOT EXISTS index_entries_entry_date  ON entries (entry_date);
CREATE INDEX IF NOT EXISTS index_entries_mood        ON entries (mood);

-- ----------------------------------------------------------------------------
-- 3. Теги. Вторая сторона связи «многие-ко-многим».
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tags (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    color      TEXT    NOT NULL DEFAULT '#4DD0A5',
    created_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags (name);

-- ----------------------------------------------------------------------------
-- 4. Промежуточная таблица «запись ↔ тег».
--    Реализует связь «многие-ко-многим»: у записи может быть много тегов,
--    и один тег может стоять у многих записей.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entry_tags (
    entry_id INTEGER NOT NULL,
    tag_id   INTEGER NOT NULL,

    -- Составной первичный ключ не даёт повесить один тег на запись дважды.
    PRIMARY KEY (entry_id, tag_id),

    FOREIGN KEY (entry_id) REFERENCES entries (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

-- Первая колонка составного PRIMARY KEY уже проиндексирована,
-- поэтому отдельный индекс нужен только для второй.
CREATE INDEX IF NOT EXISTS index_entry_tags_tag_id ON entry_tags (tag_id);

-- ----------------------------------------------------------------------------
-- 5. Виртуальная таблица полнотекстового поиска (изюминка проекта).
--    content=entries означает «внешнее содержимое»: FTS хранит только
--    поисковый индекс, а сами тексты берёт из таблицы entries. Это экономит
--    место и исключает рассинхронизацию данных.
--
--    tokenize=unicode61 задан не для галочки. Токенизатор по умолчанию
--    (simple) приводит к нижнему регистру только латиницу, поэтому запрос
--    «парк» не нашёл бы запись «Парковка»: заглавная «П» осталась бы
--    заглавной и в индексе. unicode61 приводит регистр по правилам Unicode,
--    и поиск на русском работает так, как ожидает пользователь.
-- ----------------------------------------------------------------------------
CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts4 (
    title,
    content,
    content=`entries`,
    tokenize=unicode61
);

-- Триггеры синхронизации индекса с основной таблицей.
-- Room генерирует такие же автоматически для @Fts4(contentEntity = ...).
CREATE TRIGGER IF NOT EXISTS entries_fts_after_insert
    AFTER INSERT ON entries
BEGIN
    INSERT INTO entries_fts (docid, title, content)
    VALUES (NEW.rowid, NEW.title, NEW.content);
END;

CREATE TRIGGER IF NOT EXISTS entries_fts_before_update
    BEFORE UPDATE ON entries
BEGIN
    DELETE FROM entries_fts WHERE docid = OLD.rowid;
END;

CREATE TRIGGER IF NOT EXISTS entries_fts_after_update
    AFTER UPDATE ON entries
BEGIN
    INSERT INTO entries_fts (docid, title, content)
    VALUES (NEW.rowid, NEW.title, NEW.content);
END;

CREATE TRIGGER IF NOT EXISTS entries_fts_before_delete
    BEFORE DELETE ON entries
BEGIN
    DELETE FROM entries_fts WHERE docid = OLD.rowid;
END;
