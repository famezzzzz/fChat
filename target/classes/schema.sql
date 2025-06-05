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