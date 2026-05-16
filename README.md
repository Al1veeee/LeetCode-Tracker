# LeetCode Progress Tracker

REST API на **Ktor** + **PostgreSQL** для отслеживания прогресса в LeetCode:
- регистрация и вход (JWT),
- привязка LeetCode-аккаунта,
- статистика, паттерны, стрик,
- цели по решённым задачам.

---

## 1) Что нужно для запуска

- **JDK 21+**
- **Docker / Docker Desktop** (для запуска PostgreSQL и/или всего проекта в контейнерах)
- **Git** (если будете клонировать репозиторий)

Проверка версий:

```bash
java -version
docker --version
docker compose version
```

---

## 2) Быстрый запуск (всё через Docker)

> Рекомендуемый способ, если хотите просто поднять проект и проверить API.

### Шаги

1. Соберите приложение на хосте:

   **Windows (PowerShell / CMD)**
   ```bash
   .\gradlew.bat installDist
   ```

   **Linux/macOS**
   ```bash
   chmod +x gradlew
   ./gradlew installDist
   ```

2. Запустите сервисы:

   ```bash
   docker compose up --build
   ```

3. Проверьте, что API поднялся:

   ```bash
   curl http://localhost:8080/
   ```

API будет доступен по адресу: `http://localhost:8080`.

---

## 3) Подробный гайд запуска на локальном компьютере (приложение локально, БД отдельно)

Этот режим удобен для разработки: API запускается локально, PostgreSQL можно поднять в Docker.

### Шаг 1. Клонирование и переход в папку проекта

```bash
git clone <вставьте-свой-url-репозитория>
cd LeetCode-Tracker
```

### Шаг 2. Поднимите PostgreSQL

```bash
docker compose up postgres -d
```

### Шаг 3. Настройте переменные окружения

Пример (подставьте **свои** значения):

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/leetcode_tracker
DATABASE_USER=<вставьте_своего_пользователя_бд>
DATABASE_PASSWORD=<вставьте_свой_пароль_бд>
JWT_SECRET=<вставьте_свой_длинный_секрет_минимум_32_символа>
PORT=8080
```

Если переменные окружения не заданы, используются значения по умолчанию из `build.gradle` (только для локальной разработки).

### Шаг 4. Запустите приложение

**Windows**
```bash
.\gradlew.bat run
```

**Linux/macOS**
```bash
chmod +x gradlew
./gradlew run
```

### Шаг 5. Проверьте, что всё работает

Проверка доступности сервиса:

```bash
curl http://localhost:8080/
```

---

## 4) Как пользоваться API (пример сценария)

Все маршруты вида `/users/{id}/...` требуют:

```text
Authorization: Bearer <вставьте-свой-jwt-токен>
```

### 4.1 Регистрация

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your_email@example.com",
    "password": "<вставьте_свой_пароль_минимум_8_символов>"
  }'
```

### 4.2 Вход

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your_email@example.com",
    "password": "<вставьте_свой_пароль_минимум_8_символов>"
  }'
```

Сохраните из ответа:
- `token` — JWT токен,
- `userId` — ваш ID пользователя.

### 4.3 Привязка LeetCode-профиля

```bash
curl -X POST http://localhost:8080/users/<вставьте-свой-userId>/leetcode \
  -H "Authorization: Bearer <вставьте-свой-jwt-токен>" \
  -H "Content-Type: application/json" \
  -d '{
    "leetcodeUsername": "<вставьте-свой-leetcode-username>"
  }'
```

---

## 5) Эндпоинты

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

`pattern` — ключ из каталога (например `dynamic-programming`, `hash-table`, `two-pointers`) или близкий синоним.

---

## 6) Переменные окружения

| Переменная | Что делает |
|------------|------------|
| `DATABASE_URL` | JDBC URL PostgreSQL |
| `DATABASE_USER` | Пользователь БД |
| `DATABASE_PASSWORD` | Пароль БД |
| `JWT_SECRET` | Секрет подписи JWT (обязательно используйте свой длинный случайный секрет) |
| `PORT` | HTTP порт API (по умолчанию `8080`) |

---

## 7) Структура проекта

- `src/main/kotlin/com/leetcode/tracker/application` — точка входа, JWT, синхронизация
- `src/main/kotlin/com/leetcode/tracker/api` — маршруты API
- `src/main/kotlin/com/leetcode/tracker/data` — таблицы и репозитории (Exposed)
- `src/main/kotlin/com/leetcode/tracker/domain` — модели и каталог паттернов
- `src/main/kotlin/com/leetcode/tracker/client` — клиент LeetCode GraphQL
- `src/main/resources` — конфиги Ktor / логирование

---

## 8) База данных

Используются таблицы:
- `users`
- `stats`
- `patterns`
- `goals`
- `streaks`

Схема создаётся автоматически при старте приложения (`createMissingTablesAndColumns`).

---

## 9) Важные замечания

- Публичный LeetCode GraphQL может меняться.
- Если поля по тегам недоступны, клиент делает fallback без `tagProblemCounts`.
- Для стрика используется календарь текущего года (UTC) и поле streak от LeetCode (если доступно).
