-- Users table
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL UNIQUE
    );

-- Chats table
CREATE TABLE IF NOT EXISTS chats (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(255), -- Name for group chats, can be NULL for private
    is_group BOOLEAN NOT NULL DEFAULT FALSE
    );

-- Chat_Participants table (associative table for many-to-many between users and chats)
CREATE TABLE IF NOT EXISTS chat_participants (
                                                 chat_id BIGINT NOT NULL,
                                                 user_id BIGINT NOT NULL,
                                                 PRIMARY KEY (chat_id, user_id),
    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

-- Messages table
CREATE TABLE IF NOT EXISTS messages (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        chat_id BIGINT NOT NULL,
                                        sender_id BIGINT NOT NULL,
                                        content TEXT NOT NULL,
                                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) -- Sender might not be in this specific chat if system messages are allowed, but typically is
    );

-- Sample data (optional, for testing)
INSERT INTO users (username) VALUES ('Alice'), ('Bob'), ('Charlie');

-- Sample private chat between Alice (1) and Bob (2)
-- INSERT INTO chats (is_group) VALUES (FALSE); -- chat_id will be 1
-- INSERT INTO chat_participants (chat_id, user_id) VALUES (1, 1), (1, 2);

-- Sample group chat "General" with Alice (1), Bob (2), Charlie (3)
-- INSERT INTO chats (name, is_group) VALUES ('General Chat', TRUE); -- chat_id will be 2
-- INSERT INTO chat_participants (chat_id, user_id) VALUES (2, 1), (2, 2), (2, 3);