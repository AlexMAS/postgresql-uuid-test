# postgresql-uuid-test

Тесты для анализа поведения PostgreSQL при использовании разных версий UUID в качестве первичного ключа таблицы.

Тесты создают таблицу вида "ключ-значение", где в качестве ключа используется указанная версия UUID, а в качестве
значения - массив байт фиксированного размера (1Кб). Тесты не производят мониторинг и анализ показателей базы данных,
предполагая, что это делается внешними инструментами.

О целях тестирования можно прочитать в моей статье "[Выбор UUID для первичного ключа таблицы](https://dzen.ru/a/Z9uUsNJmQA0AbY-c)".

## Запуск в Docker

Для простоты отладки тест может быть запущен в Docker.

### Вставка данных

Для запуска теста на вставку данных в таблицу нужно выполнить команду:

```sh
./start.sh insert KEY_TYPE ROW_COUNT
```

где `KEY_TYPE` - это один из типов ключей: `BigSerial`, `uuid1`, `uuid4`, `uuid6`, `uuid7`;
`ROW_COUNT` - количество строк для вставки.

### Выборка данных

Для запуска теста на чтение случайных ключей из ранее наполненной таблицы нужно выполнить команду:

```sh
./start.sh select KEY_TYPE ROW_COUNT
```

где `KEY_TYPE` - это один из типов ключей: `BigSerial`, `uuid1`, `uuid4`, `uuid6`, `uuid7`;
`ROW_COUNT` - количество строк для выборки.

## Сборка проекта

Для сборки проекта:

```sh
./gradlew build
```

## Зависимости проекта

Код теста помещается в [один файл](postgresql-uuid-test/src/main/java/org/sandbox/uuid/db/Program.java) и зависит от двух пакетов:

* [PostgreSQL JDBC Driver](https://github.com/pgjdbc/pgjdbc)
* [Java Uuid Generator (JUG)](https://github.com/cowtowncoder/java-uuid-generator)
