# Chat Application

This is a RESTful chat application built with **Spring Boot**, **Apache Camel**, **Spring Security**, **Hibernate**, and **SQLite**. It supports user registration, authentication, group creation, private messaging, group messaging, and group membership management. The application uses a modular design with Camel routes for handling API requests and WebSocket for real-time message delivery.

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Key Components](#key-components)
3. [Database Schema](#database-schema)
4. [API Endpoints](#api-endpoints)
5. [How It Works](#how-it-works)
6. [Setup and Running](#setup-and-running)
7. [Troubleshooting](#troubleshooting)

## Architecture Overview
The application follows a layered architecture:
- **Presentation Layer**: REST API endpoints (`/api/*`) defined using Apache Camel’s REST DSL, secured with JWT-based authentication.
- **Business Logic Layer**: Camel routes (`ChatRoute.java`) handle request processing, interacting with repositories and WebSocket for message delivery.
- **Persistence Layer**: Hibernate ORM with JPA manages database operations, using SQLite as the backend.
- **Security**: Spring Security with JWT ensures authenticated access to protected endpoints.
- **Real-Time Messaging**: Spring WebSocket (`SimpMessagingTemplate`) pushes messages to clients subscribed to `/topic/private/{userId}` or `/topic/group/{groupId}`.

The application runs on an embedded Tomcat server (port `38080`) and stores data in a SQLite database (`/Users/urijvazmin/chat.db`).

## Key Components

### 1. Entities (`ru.top.server.model`)
- **ChatUser**:
  - Table: `chat_user`
  - Fields: `id` (UUID, primary key), `username`, `password` (BCrypt-encoded).
  - Represents a user, linked to messages and groups via relationships.
- **ChatGroup**:
  - Table: `chat_group`
  - Fields: `id` (UUID, primary key), `name`.
  - Represents a chat group, linked to messages and users.
- **ChatUserGroups**:
  - Table: `chat_user_groups`
  - Fields: `id` (auto-increment), `user_id`, `group_id`.
  - Manages many-to-many relationships between users and groups.
- **Message**:
  - Table: `chat_message`
  - Fields: `id` (UUID, primary key), `content`, `sender_id`, `recipient_id`, `group_id`, `chat_type` (`PRIVATE` or `GROUP`), `timestamp`.
  - Represents private or group messages, with foreign keys to `chat_user` and `chat_group`.

### 2. Routes (`ChatRoute.java`)
Apache Camel routes define the API logic:
- **User Management**:
  - `/api/users/register`: Creates a user with a unique username and encoded password.
  - `/api/auth/login`: Authenticates users and returns a JWT.
- **Group Management**:
  - `/api/groups`: Creates a group with a unique name.
  - `/api/groups/join`: Adds a user to a group, populating `chat_user_groups`.
- **Messaging**:
  - `/api/messages/private`: Sends a private message, pushing it via WebSocket to `/topic/private/{recipientId}`.
  - `/api/messages/private/{userId}`: Retrieves private messages for a user.
  - `/api/messages/group`: Sends a group message, pushing it to `/topic/group/{groupId}`.
  - `/api/messages/group/{groupId}`: Retrieves group messages.

### 3. Security (`SecurityConfig.java`, `JwtUtil.java`, `JwtAuthenticationFilter.java`)
- **Authentication**: Spring Security uses JWTs generated during login.
- **Authorization**:
  - Public endpoints: `/api/auth/login`, `/api/users/register`, `/api/groups`.
  - Protected endpoints: `/api/messages/*`, `/api/groups/join` require `ROLE_USER`.
- **JWT Flow**:
  1. User logs in, receiving a JWT with `username` and `ROLE_USER`.
  2. `JwtAuthenticationFilter` validates JWTs for protected requests, setting the security context.

### 4. WebSocket (`SimpMessagingTemplate`)
- Real-time messaging is handled via WebSocket topics:
  - Private messages: `/topic/private/{userId}`.
  - Group messages: `/topic/group/{groupId}`.
- Messages are pushed after being saved to the database.

### 5. Persistence (`application.properties`, `init.sql`)
- **Database**: SQLite at `/Users/urijvazmin/chat.db`.
- **Hibernate**: Configured with `spring.jpa.hibernate.ddl-auto=none` to avoid schema modification issues.
- **Schema Initialization**: `init.sql` creates tables with foreign keys.

## Database Schema
The SQLite database (`chat.db`) has four tables:
```sql
CREATE TABLE chat_user (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL
);

CREATE TABLE chat_group (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE chat_user_groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES chat_user(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES chat_group(id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    id TEXT PRIMARY KEY,
    content TEXT,
    sender_id TEXT,
    recipient_id TEXT,
    group_id TEXT,
    chat_type TEXT,
    timestamp DATETIME,
    FOREIGN KEY (sender_id) REFERENCES chat_user(id),
    FOREIGN KEY (recipient_id) REFERENCES chat_user(id),
    FOREIGN KEY (group_id) REFERENCES chat_group(id)
);
```

## API Endpoints

### 1. Register User
- **POST** `/users/register`
- **Body**:
  ```json
  {"username":"urnassme","password":"password123"}
  ```
- **Response**: `200 OK`
  ```json
  {"message":"User registered successfully","id":"uuid","username":"urnassme"}
  ```
- **Logic**: Saves user with BCrypt-encoded password.

### 2. Login
- **POST** `/auth/login`
- **Body**:
  ```json
  {"username":"urnassme","password":"password123"}
  ```
- **Response**: `200 OK`
  ```json
  {"token":"jwt"}
  ```
- **Logic**: Authenticates user, generates JWT.

### 3. Create Group
- **POST** `/groups`
- **Body**:
  ```json
  {"name":"Group_q"}
  ```
- **Response**: `200 OK`
  ```json
  {"message":"Group created successfully","id":"uuid","name":"Group_q"}
  ```
- **Logic**: Saves group with unique name.

### 4. Join Group
- **POST** `/groups/join`
- **Headers**: `Authorization: Bearer [jwt]`
- **Body**:
  ```json
  {"groupId":"uuid"}
  ```
- **Response**: `200 OK`
  ```json
  {"message":"Joined group successfully","userId":"uuid","groupId":"uuid"}
  ```
- **Logic**: Creates a `ChatUserGroups` entry.

### 5. Send Private Message
- **POST** `/messages/private`
- **Headers**: `Authorization: Bearer [jwt]`
- **Body**:
  ```json
  {"content":"Hello","senderId":"uuid","recipientId":"uuid"}
  ```
- **Response**: `200 OK`
  ```json
  {"message":"Message sent successfully"}
  ```
- **Logic**: Saves message, pushes to `/topic/private/{recipientId}`.

### 6. Get Private Messages
- **GET** `/messages/private/{userId}`
- **Headers**: `Authorization: Bearer [jwt]`
- **Response**: `200 OK`
  ```json
  [{"id":"uuid","content":"Hello","senderId":"uuid","recipientId":"uuid","chatType":"PRIVATE","timestamp":"..."}]
  ```
- **Logic**: Queries messages where `recipient_id = userId` and `chat_type = PRIVATE`.

### 7. Send Group Message
- **POST** `/messages/group`
- **Headers**: `Authorization: Bearer [jwt]`
- **Body**:
  ```json
  {"content":"Hello Group","senderId":"uuid","groupId":"uuid"}
  ```
- **Response**: `200 OK`
  ```json
  {"message":"Message sent successfully"}
  ```
- **Logic**: Saves message, pushes to `/topic/group/{groupId}`.

### 8. Get Group Messages
- **GET** `/messages/group/{groupId}`
- **Headers**: `Authorization: Bearer [jwt]`
- **Response**: `200 OK`
  ```json
  [{"id":"uuid","content":"Hello Group","senderId":"uuid","groupId":"uuid","chatType":"GROUP","timestamp":"..."}]
  ```
- **Logic**: Queries messages where `group_id = groupId` and `chat_type = GROUP`.

## How It Works
1. **Startup**:
   - Spring Boot initializes the application, loading `application.properties`.
   - SQLite database is initialized with `init.sql`.
   - Hibernate maps entities to tables, using `none` for DDL to avoid SQLite limitations.
   - Camel routes are configured for REST endpoints.
   - Spring Security sets up JWT authentication.

2. **User Flow**:
   - **Registration**: User submits username/password, saved with BCrypt encoding.
   - **Login**: User authenticates, receives JWT for subsequent requests.
   - **Group Creation/Joining**: Users create groups and join them, populating `chat_user_groups`.
   - **Messaging**:
     - Private messages are saved and pushed via WebSocket to the recipient.
     - Group messages are saved and pushed to group subscribers.
     - Messages are retrieved via GET requests, filtered by recipient or group.

3. **Request Processing**:
   - **REST Request**: Hits a Camel route (e.g., `direct:sendPrivateMessage`).
   - **Security**: `JwtAuthenticationFilter` validates JWT, sets `SecurityContext`.
   - **Business Logic**: Route validates input, queries repositories, saves data via JPA.
   - **WebSocket**: Pushes messages to clients.
   - **Response**: Returns JSON (e.g., `{"message":"Message sent successfully"}`).

4. **Data Flow Example** (Send Private Message):
   - Request: `POST /api/messages/private` with JWT and body.
   - Route: Validates sender matches authenticated user, saves message via JPA.
   - WebSocket: Pushes message to `/topic/private/{recipientId}`.
   - Database: Stores message in `chat_message`.
   - Response: `200 OK` with success message.

## Setup and Running
1. **Prerequisites**:
   - Java 17
   - Maven
   - SQLite

2. **Clone Repository**:
   ```bash
   cd /Users/urijvazmin/Downloads/ChatServer/fChat
   ```

3. **Initialize Database**:
   ```bash
   rm /Users/urijvazmin/chat.db
   sqlite3 /Users/urijvazmin/chat.db < init.sql
   ```

4. **Build and Run**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

5. **Test Endpoints**:
   - Register: `curl -X POST http://localhost:38080/api/users/register -H "Content-Type: application/json" -d '{"username":"urnassme","password":"password123"}'`
   - Login: `curl -X POST http://localhost:38080/api/auth/login -H "Content-Type: application/json" -d '{"username":"urnassme","password":"password123"}'`
   - Use JWT for protected endpoints.

## Troubleshooting
- **DDL Errors**:
  - Check `spring.jpa.hibernate.ddl-auto=none` in `application.properties`.
  - Reinitialize database with `init.sql`.
- **403 Forbidden**:
  - Verify JWT validity and `ROLE_USER` in `SecurityConfig.java`.
  - Temporarily set `.anyRequest().permitAll()` to test.
- **Empty Responses**:
  - Query database: `SELECT * FROM chat_message WHERE ...`.
  - Check logs: `/Users/urijvazmin/server.log`.
- **Serialization Issues**:
  - Ensure `@JsonIgnoreProperties` in `Message.java`.
  - Verify `ObjectMapper` usage in routes.

## Logs
- Location: `/Users/urijvazmin/server.log`
- Key Entries:
  - `Retrieved X messages for groupId/userId`
  - `Pushed private/group message to /topic/...`
  - Hibernate SQL queries