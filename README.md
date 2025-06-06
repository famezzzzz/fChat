# Чат-приложение

Это RESTful чат-приложение, построенное с использованием **Spring Boot**, **Apache Camel**, **Spring Security**, **Hibernate** и **SQLite**. Приложение поддерживает регистрацию пользователей, аутентификацию, создание групп, отправку личных и групповых сообщений, а также управление членством в группах. Приложение использует модульный дизайн с маршрутами Camel для обработки API-запросов и WebSocket для доставки сообщений в реальном времени.

## Содержание
1. [Обзор архитектуры](#обзор-архитектуры)
2. [Основные компоненты](#основные-компоненты)
3. [Схема базы данных](#схема-базы-данных)
4. [API-эндпоинты](#api-эндпоинты)
5. [Как это работает](#как-это-работает)
6. [Настройка и запуск](#настройка-и-запуск)
7. [Устранение неполадок](#устранение-неполадок)

## Обзор архитектуры
Приложение имеет многослойную архитектуру:
- **Слой представления**: REST API-эндпоинты (`/api/*`), определённые с использованием REST DSL Apache Camel, защищённые JWT-аутентификацией.
- **Слой бизнес-логики**: Маршруты Camel (`ChatRoute.java`) обрабатывают запросы, взаимодействуя с репозиториями и WebSocket для доставки сообщений.
- **Слой хранения данных**: Hibernate ORM с JPA управляет операциями с базой данных, используя SQLite в качестве бэкенда.
- **Безопасность**: Spring Security с JWT обеспечивает аутентифицированный доступ к защищённым эндпоинтам.
- **Мгновенные сообщения**: Spring WebSocket (`SimpMessagingTemplate`) отправляет сообщения клиентам, подписанным на `/topic/private/{userId}` или `/topic/group/{groupId}`.

Приложение работает на встроенном сервере Tomcat (порт `38080`) и хранит данные в базе SQLite (`/Users/urijvazmin/chat.db`).

## Основные компоненты

### 1. Сущности (`ru.top.server.model`)
- **ChatUser**:
    - Таблица: `chat_user`
    - Поля: `id` (UUID, первичный ключ), `username`, `password` (закодирован с помощью BCrypt), `birthdate`, `email`, `phone`, `avatar_url`.
    - Представляет пользователя, связанного с сообщениями и группами через отношения.
- **ChatGroup**:
    - Таблица: `chat_group`
    - Поля: `id` (UUID, первичный ключ), `name`.
    - Представляет группу чата, связанную с сообщениями и пользователями.
- **ChatUserGroups**:
    - Таблица: `chat_user_groups`
    - Поля: `id` (автоинкремент), `user_id`, `group_id`.
    - Управляет отношениями многие-ко-многим между пользователями и группами.
- **Message**:
    - Таблица: `chat_message`
    - Поля: `id` (UUID, первичный ключ), `content`, `sender_id`, `recipient_id`, `group_id`, `chat_type` (`PRIVATE` или `GROUP`), `timestamp`.
    - Представляет личные или групповые сообщения, с внешними ключами на `chat_user` и `chat_group`.

### 2. Маршруты (`ChatRoute.java`)
Маршруты Apache Camel определяют логику API:
- **Управление пользователями**:
    - `/api/users/register`: Создаёт пользователя с уникальным именем, закодированным паролем, датой рождения, email, телефоном и URL аватара.
    - `/api/auth/login`: Аутентифицирует пользователей и возвращает JWT.
    - `/api/users/{userId}`: Возвращает информацию о конкретном пользователе (ID, имя, дата рождения, email, телефон, URL аватара).
    - `/api/users/`: Возвращает информацию об авторизованном пользователе (ID, имя, дата рождения, email, телефон, URL аватара).
- **Управление группами**:
    - `/api/groups`: Создаёт группу с уникальным именем.
    - `/api/groups/join`: Добавляет пользователя в группу, заполняющей `chat_user_groups`.
- **Сообщения**:
    - `/api/messages/private`: Отправляет личное сообщение, доставляя его через WebSocket на `/topic/private/{recipientId}`.
    - `/api/messages/private/{userId}`: Получает личные сообщения для пользователя.
    - `/api/messages/group`: Отправляет групповое сообщение, доставляя его на `/topic/group/{groupId}`.
    - `/api/messages/group/{groupId}`: Получает групповые сообщения.
    - `/api/messages/search`: Ищет сообщения по ключевым словам и/или временному диапазону.

### 3. Безопасность (`SecurityConfig.java`, `JwtUtil.java`, `JwtAuthenticationFilter.java`)
- **Аутентификация**: Spring Security использует JWT, сгенерированные при входе.
- **Авторизация**:
    - Публичные эндпоинты: `/api/auth/login`, `/api/users/register`, `/api/groups`.
    - Защищённые эндпоинты: `/api/messages/*`, `/api/users/*`, `/api/groups/join` требуют роль `ROLE_USER`.
- **Поток JWT**:
    1. Пользователь входит, получая JWT с `username` и `ROLE_USER`.
    2. `JwtAuthenticationFilter` проверяет JWT для защищённых запросов, устанавливая контекст безопасности.

### 4. WebSocket (`SimpMessagingTemplate`)
- Мгновенные сообщения обрабатываются через темы WebSocket:
    - Личные сообщения: `/topic/private/{userId}`.
    - Групповые сообщения: `/topic/group/{groupId}`.
- Сообщения отправляются после сохранения в базу данных.

### 5. Хранилище данных (`application.properties`, `init.sql`)
- **База данных**: SQLite в `/Users/urijvazmin/chat.db`.
- **Hibernate**: Настроен с `spring.jpa.hibernate.ddl-auto=none` для избежания проблем с модификацией схемы.
- **Инициализация схемы**: `init.sql` создаёт таблицы с внешними ключами.

## Схема базы данных
База данных SQLite (`chat.db`) содержит четыре таблицы:
```sql
CREATE TABLE IF NOT EXISTS chat_user (
                                         id TEXT PRIMARY KEY,
                                         username TEXT NOT NULL UNIQUE,
                                         password TEXT NOT NULL,
                                         birthdate DATE,
                                         email TEXT UNIQUE,
                                         phone TEXT,
                                         avatar_url TEXT
);

CREATE TABLE IF NOT EXISTS chat_group (
                                          id TEXT PRIMARY KEY,
                                          name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_user_groups (
                                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                                user_id TEXT NOT NULL,
                                                group_id TEXT NOT NULL,
                                                FOREIGN KEY (user_id) REFERENCES chat_user(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES chat_group(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS chat_message (
                                            id TEXT PRIMARY KEY,
                                            content TEXT NOT NULL,
                                            sender_id TEXT,
                                            recipient_id TEXT,
                                            group_id TEXT,
                                            chat_type TEXT NOT NULL,
                                            timestamp DATETIME NOT NULL,
                                            FOREIGN KEY (sender_id) REFERENCES chat_user(id),
    FOREIGN KEY (recipient_id) REFERENCES chat_user(id),
    FOREIGN KEY (group_id) REFERENCES chat_group(id)
    );
```

## API-эндпоинты

### 1. Регистрация пользователя
- **POST** `/api/users/register`
- **Тело запроса**:
  ```json
  {
    "username": "user_name",
    "password": "password123",
    "birthdate": "01-01-1990",
    "email": "user@example.com",
    "phone": "+1234567890",
    "avatarUrl": "https://example.com/avatar.jpg"
  }
  ```
- **Примечание**: Поле `birthdate` должно быть в формате `MM-dd-yyyy` (например, `01-01-1990`).
- **Ответ**: `200 OK`
  ```json
  {"message":"User registered successfully","id":"uuid","username":"user_name","email":"user@example.com"}
  ```
- **Логика**: Сохраняет пользователя с закодированным паролем (BCrypt) и дополнительными полями. Поля `birthdate`, `email`, `phone`, `avatarUrl` опциональны, но `email` должен быть уникальным, если указан.

### 2. Получение информации о пользователе
- **GET** `/api/users/{userId}`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Ответ**: `200 OK`
  ```json
  {
    "id": "uuid",
    "username": "user_name",
    "birthdate": "01-01-1990",
    "email": "user@example.com",
    "phone": "+1234567890",
    "avatarUrl": "https://example.com/avatar.jpg"
  }
  ```
- **Логика**: Возвращает информацию о пользователе по `userId`. Возвращает 404, если пользователь не найден.

### 3. Вход
- **POST** `/api/auth/login`
- **Тело запроса**:
  ```json
  {"username":"user_name","password":"password123"}
  ```
- **Ответ**: `200 OK`
  ```json
  {"token":"jwt"}
  ```
- **Логика**: Аутентифицирует пользователя, возвращает JWT.

### 4. Создание группы
- **POST** `/api/groups`
- **Тело запроса**:
  ```json
  {"name":"Group_q"}
  ```
- **Ответ**: `200 OK`
  ```json
  {"message":"Group created successfully","id":"uuid","name":"Group_q"}
  ```
- **Логика**: Сохраняет группу с уникальным именем.

### 5. Отправка личного сообщения
- **POST** `/api/messages/private`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Тело запроса**:
  ```json
  {"content":"Hello","senderId":"uuid","recipientId":"uuid"}
  ```
- **Ответ**: `200 OK`
  ```json
  {"message":"Message sent successfully"}
  ```

### 6. Получение личных сообщений
- **GET** `/api/messages/private/{userId}`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Ответ**: `200 OK`
  ```json
  [{"id":"uuid","content":"Hello","senderId":"uuid","recipientId":"uuid","chatType":"PRIVATE","timestamp":"..."}]
  ```
- **Логика**: Возвращает сообщения, где `recipient_id = userId` и `chat_type = PRIVATE`.

### 7. Отправка группового сообщения
- **POST** `/api/messages/group`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Тело запроса**:
  ```json
  {"content":"Hello Group","senderId":"uuid","groupId":"uuid"}
  ```
- **Ответ**: `200 OK`
  ```json
  {"message":"Message sent successfully"}
  ```

### 8. Получение групповых сообщений
- **GET** `/api/messages/group/{groupId}`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Ответ**: `200 OK`
  ```json
  [{"id":"uuid","content":"Hello Group","senderId":"uuid","groupId":"uuid","chatType":"GROUP","timestamp":"..."}]
  ```
- **Логика**: Возвращает сообщения, где `group_id = groupId` и `chat_type = GROUP`.

### 9. Поиск сообщений
- **GET** `/api/messages/search?keyword={keyword}&start={start}&end={end}`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Параметры**:
    - `keyword` (опционально): строка для поиска в содержимом сообщений (например, `hello`).
    - `start` (опционально): начальная дата/время в формате ISO (например, `2025-06-01T00:00:00`).
    - `end` (опционально): конечная дата/время в формате ISO (например, `2025-06-03T23:59:59`).
- **Пример запроса**:
  ```bash
  curl -X GET "http://localhost:38080/api/messages/search?keyword=hello&start=2025-06-01T00:00:00&end=2025-06-03T23:59:59" -H "Authorization: Bearer [jwt]"
  ```
- **Ответ**: `200 OK`
  ```json
  [{"id":"uuid","content":"Hello","senderId":"uuid","recipientId":"uuid","chatType":"PRIVATE","timestamp":"..."}]
  ```
- **Логика**: Возвращает сообщения, где пользователь является отправителем, получателем или участником группы, с фильтрацией по ключевому слову и/или временному диапазону. Если параметры не указаны, возвращаются все доступные сообщения пользователя.

### 10. Получение количества зарегистрированных пользователей
- **GET** `/api/users/count`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Ответ**: `200 OK`
  ```json
  {"count":2}
  ```
- **Логика**: Возвращает количество записей из таблицы `chat_user`

### 11. Получение информации о себе
- **GET** `/api/users/myInfo`
- **Заголовки**: `Authorization: Bearer [jwt]`
- **Ответ**: `200 OK`
  ```json
  {
    "id": "uuid",
    "username": "user_name",
    "birthdate": "01-01-1990",
    "email": "user@example.com",
    "phone": "+1234567890",
    "avatarUrl": "https://example.com/avatar.jpg"
  }
  ```
- **Логика**: Возвращает информацию о пользователе по JWT токену.

## Как это работает
1. **Запуск**:
    - Spring Boot инициализирует приложение, загружая `application.properties`.
    - База SQLite инициализируется с помощью `init.sql`.
    - Hibernate сопоставляет сущности с таблицами, используя `none` для DDL.
    - Маршруты Camel настраиваются для REST-эндпоинтов.
    - Spring Security настраивает JWT-аутентификацию.

2. **Поток пользователя**:
    - **Регистрация**: Пользователь отправляет имя, пароль, дату рождения, email, телефон и URL аватара; данные сохраняются с кодировкой BCrypt.
    - **Вход**: Пользователь аутентифицируется, получает JWT для последующих запросов.
    - **Получение информации о пользователе**: Пользователь запрашивает данные другого пользователя по ID.
    - **Создание/присоединение к группам**: Пользователи создают группы и присоединяются к ним, заполняя `chat_user_groups`.
    - **Сообщения**:
        - Личные сообщения сохраняются и отправляются получателю через WebSocket.
        - Групповые сообщения сохраняются и отправляются подписчикам группы.
        - Сообщения извлекаются через GET-запросы с фильтрацией по получателю, группе или ключевым словам/временному диапазону.

3. **Обработка запросов**:
    - **REST-запрос**: Обрабатывается маршрутом Camel (например, `direct:sendPrivateMessage`).
    - **Безопасность**: `JwtAuthenticationFilter` проверяет JWT, устанавливает контекст.
    - **Бизнес-логика**: Маршрут проверяет входные данные, запрашивает репозитории, сохраняет данные через JPA.
    - **WebSocket**: Отправляет сообщения клиентам.
    - **Ответ**: Возвращает JSON (например, `{"message":"Message sent successfully"}`).

4. **Пример потока данных** (Поиск сообщений):
    - Запрос: `GET /api/messages/search?keyword=hello&start=2025-06-01T00:00:00` с JWT.
    - Маршрут: Проверяет аутентифицированного пользователя, выполняет запрос JPA с фильтрацией по ключевому слову и времени.
    - База данных: Возвращает список сообщений, соответствующих критериям.
    - Ответ: `200 OK` с JSON-массивом сообщений.

## Настройка и запуск
1. **Требования**:
    - Java 17
    - Maven
    - SQLite

2. **Клонирование репозитория**:
   ```bash
   cd /Users/urijvazmin/Downloads/ChatServer/fChat
   ```

3. **Инициализация базы данных**:
   ```bash
   rm /Users/urijvazmin/chat.db
   sqlite3 /Users/urijvazmin/chat.db < init.sql
   ```

4. **Сборка и запуск**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

5. **Тестирование эндпоинтов**:
    - Регистрация:
      ```bash
      curl -X POST http://localhost:38080/api/users/register -H "Content-Type: application/json" -d '{"username":"user_name","password":"password123","birthdate":"01-01-1990","email":"user@example.com","phone":"+1234567890","avatarUrl":"https://example.com/avatar.jpg"}'
      ```
    - Получение информации о пользователе:
      ```bash
      curl -X GET http://localhost:38080/api/users/{userId} -H "Authorization: Bearer [jwt]"
      ```
    - Вход:
      ```bash
      curl -X POST http://localhost:38080/api/auth/login -H "Content-Type: application/json" -d '{"username":"user_name","password":"password123"}'
      ```
    - Поиск сообщений:
      ```bash
      curl -X GET "http://localhost:38080/api/messages/search?keyword=hello&start=2025-06-01T00:00:00" -H "Authorization: Bearer [jwt]"
      ```
    - Используйте JWT для защищённых эндпоинтов.

## Устранение неполадок
- **Ошибки DDL**:
    - Проверьте `spring.jpa.hibernate.ddl-auto=none` в `application.properties`.
    - Переинициализируйте базу данных с помощью `init.sql`.
- **403 Forbidden**:
    - Проверьте валидность JWT и наличие `ROLE_USER` в `SecurityConfig.java`.
    - Временно установите `.anyRequest().permitAll()` для теста.
- **Пустые ответы**:
    - Выполните запрос к базе: `SELECT * FROM chat_message WHERE ...`.
    - Проверьте логи: `/Users/urijvazmin/server.log`.
- **Ошибки сериализации**:
    - Убедитесь, что в `Message.java` есть `@JsonIgnoreProperties`.
    - Проверьте использование `ObjectMapper` в маршрутах.
- **Ошибка InvalidPayloadRuntimeException в `/api/messages/search`**:
    - Убедитесь, что запрос использует корректный формат параметров (`keyword`, `start`, `end`).
    - Проверьте, что база данных содержит сообщения, соответствующие критериям поиска.
    - Проверьте логи на наличие ошибок JPA или Hibernate, таких как `SQLITE_ERROR`.
    - Убедитесь, что `Message.java` содержит правильный именованный запрос `Message.searchMessages`.
- **Ошибки десериализации даты**:
    - Убедитесь, что поле `birthdate` в запросе `/api/users/register` соответствует формату `MM-dd-yyyy` (например, `01-01-1990`).
    - Проверьте логи на наличие `DateTimeParseException`.

## Логи
- Расположение: `/Users/urijvazmin/server.log`
- Ключевые записи:
    - `Retrieved X messages for groupId/userId`
    - `Pushed private/group message to /topic/...`
    - `Failed to search messages: ...`
    - SQL-запросы Hibernate