# LeetCode Progress Tracker

REST API на **Ktor**, **PostgreSQL**, **Exposed**, с интеграцией **LeetCode GraphQL** (`https://leetcode.com/graphql`), JWT-аутентификацией, учётом паттернов, стриков и целей.

## Требования

- **JDK 21+** (для локальной разработки; Gradle 9.4 поддерживает и более новые JVM, например 26).
- **Docker Desktop** (для `docker compose`).

## Быстрый старт (Docker)

Сначала соберите дистрибутив на хосте (в Docker-сети у части пользователей Maven Central недоступен по TLS):

```bash
.\gradlew.bat installDist
docker compose up --build
```

На Linux/macOS: `./gradlew installDist` затем `docker compose up --build`.

API: `http://localhost:8080`

Переменные окружения для сервиса `api` (см. `docker-compose.yml`):

| Переменная | Описание |
|------------|----------|
| `DATABASE_URL` | JDBC URL PostgreSQL |
| `DATABASE_USER` | пользователь БД |
| `DATABASE_PASSWORD` | пароль БД |
| `JWT_SECRET` | секрет подписи JWT (обязательно смените в проде) |
| `PORT` | порт HTTP (по умолчанию 8080) |

## Локальная разработка

1. Поднимите PostgreSQL (или используйте только Docker для БД):

   ```bash
   docker compose up postgres -d
   ```

2. Сборка и запуск:

   ```bash
   .\gradlew.bat run
   ```

   На Linux/macOS: `chmod +x gradlew && ./gradlew run`.

По умолчанию приложение подключается к `jdbc:postgresql://localhost:5432/leetcode_tracker`, пользователь/пароль `tracker` / `tracker`, если не заданы `DATABASE_*`.

## Эндпоинты

Все маршруты `/users/{id}/...` требуют заголовок:

`Authorization: Bearer <token>`

| Метод | Путь | Описание |
|--------|------|----------|
| POST | `/auth/register` | Регистрация `{ "email", "password" }` (пароль ≥ 8 символов) |
| POST | `/auth/login` | Вход `{ "email", "password" }` |
| POST | `/users/{id}/leetcode` | Привязать LeetCode username, синхронизировать статистику |
| GET | `/users/{id}/stats` | Общая статистика; `?refresh=true` — повторно запросить LeetCode |
| GET | `/users/{id}/patterns` | Прогресс по паттернам (каталог + числа из LeetCode по тегам) |
| GET | `/users/{id}/streak` | Стрик (календарь LeetCode + вычисление по дням с решениями) |
| POST | `/users/{id}/goals` | Цель: `{ "pattern", "targetTotalSolved", "deadline" }` (дата `yyyy-MM-dd`) |
| GET | `/users/{id}/goals` | Прогресс по целям (осталось задач, дней, среднее в день) |

`pattern` — ключ из внутреннего каталога (например `dynamic-programming`, `hash-table`, `two-pointers`) или близкие синонимы.

## Структура пакетов

- `application` — точка входа, JWT, синхронизация с LeetCode
- `domain` — модели и каталог паттернов
- `data` — таблицы Exposed и репозитории
- `api` — маршруты
- `client` — HTTP-клиент к LeetCode GraphQL

## База данных

Таблицы: `users`, `stats`, `patterns`, `goals`, `streaks`. Схема создаётся при старте (`createMissingTablesAndColumns`).

## Примечания по LeetCode

Публичный GraphQL может меняться; при ошибке полей с тегами выполняется запрос без `tagProblemCounts`. Для стрика используется календарь текущего года (UTC) и поле streak с LeetCode, если доступно.
