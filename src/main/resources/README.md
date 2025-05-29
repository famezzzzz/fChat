# Chat Application API

This is a RESTful chat application built with Spring Boot, Apache Camel, and SQLite. It supports user registration, authentication, group creation, private messaging, group messaging, and group joining.

## Base URL
`http://localhost:38080/api`

## Endpoints

### 1. Register User
- **Method**: POST
- **Path**: `/users/register`
- **Content-Type**: `application/json`
- **Body**:
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "message": "User registered successfully",
    "id": "uuid",
    "username": "string"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/users/register \
       -H "Content-Type: application/json" \
       -d '{"username":"urnassme","password":"password123"}'
  ```

### 2. Login
- **Method**: POST
- **Path**: `/auth/login`
- **Content-Type**: `application/json`
- **Body**:
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "token": "jwt"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username":"urnassme","password":"password123"}'
  ```

### 3. Create Group
- **Method**: POST
- **Path**: `/groups`
- **Content-Type**: `application/json`
- **Body**:
  ```json
  {
    "name": "string"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "message": "Group created successfully",
    "id": "uuid",
    "name": "string"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/groups \
       -H "Content-Type: application/json" \
       -d '{"name":"Group_q"}'
  ```

### 4. Join Group
- **Method**: POST
- **Path**: `/groups/join`
- **Content-Type**: `application/json`
- **Authorization**: `Bearer [jwt]`
- **Body**:
  ```json
  {
    "groupId": "uuid"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "message": "Joined group successfully",
    "userId": "uuid",
    "groupId": "uuid"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/groups/join \
       -H "Content-Type: application/json" \
       -H "Authorization: Bearer [jwt]" \
       -d '{"groupId":"[group-uuid]"}'
  ```

### 5. Send Private Message
- **Method**: POST
- **Path**: `/messages/private`
- **Content-Type**: `application/json`
- **Authorization**: `Bearer [jwt]`
- **Body**:
  ```json
  {
    "content": "string",
    "senderId": "uuid",
    "recipientId": "uuid"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "message": "Message sent successfully"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/messages/private \
       -H "Content-Type: application/json" \
       -H "Authorization: Bearer [jwt]" \
       -d '{"content":"Hello Recipient","senderId":"[urnassme-uuid]","recipientId":"[recipient-uuid]"}'
  ```

### 6. Get Private Messages
- **Method**: GET
- **Path**: `/messages/private/{userId}`
- **Authorization**: `Bearer [jwt]`
- **Response**: 200 OK
  ```json
  [
    {
      "id": "uuid",
      "content": "string",
      "senderId": "uuid",
      "recipientId": "uuid",
      "chatType": "PRIVATE",
      "timestamp": "yyyy-MM-dd'T'HH:mm:ss"
    }
  ]
  ```
- **Example**:
  ```bash
  curl -X GET http://localhost:38080/api/messages/private/[recipient-uuid] \
       -H "Authorization: Bearer [jwt]"
  ```

### 7. Send Group Message
- **Method**: POST
- **Path**: `/messages/group`
- **Content-Type**: `application/json`
- **Authorization**: `Bearer [jwt]`
- **Body**:
  ```json
  {
    "content": "string",
    "senderId": "uuid",
    "groupId": "uuid"
  }
  ```
- **Response**: 200 OK
  ```json
  {
    "message": "Message sent successfully"
  }
  ```
- **Example**:
  ```bash
  curl -X POST http://localhost:38080/api/messages/group \
       -H "Content-Type: application/json" \
       -H "Authorization: Bearer [jwt]" \
       -d '{"content":"Hello Group","senderId":"[urnassme-uuid]","groupId":"[group-uuid]"}'
  ```

### 8. Get Group Messages
- **Method**: GET
- **Path**: `/messages/group/{groupId}`
- **Authorization**: `Bearer [jwt]`
- **Response**: 200 OK
  ```json
  [
    {
      "id": "uuid",
      "content": "string",
      "senderId": "uuid",
      "groupId": "uuid",
      "chatType": "GROUP",
      "timestamp": "yyyy-MM-dd'T'HH:mm:ss"
    }
  ]
  ```
- **Example**:
  ```bash
  curl -X GET http://localhost:38080/api/messages/group/[group-uuid] \
       -H "Authorization: Bearer [jwt]"
  ```

## Setup
1. Clone the repository.
2. Initialize the database:
   ```bash
   sqlite3 /Users/urijvazmin/chat.db < init.sql
   ```
3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

## Notes
- SQLite is used as the database (`/Users/urijvazmin/chat.db`).
- Logs are written to `/Users/urijvazmin/server.log`.
- Ensure valid JWT for authenticated endpoints.
- Database schema is defined in `init.sql`.