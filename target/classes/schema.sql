CREATE TABLE IF NOT EXISTS CHAT_USER (
                                         id TEXT PRIMARY KEY,
                                         username TEXT NOT NULL UNIQUE,
                                         password TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS CHAT_GROUP (
                                          id TEXT PRIMARY KEY,
                                          name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS CHAT_GROUP_CHAT_USER (
                                                    chat_group_id TEXT,
                                                    chat_user_id TEXT,
                                                    PRIMARY KEY (chat_group_id, chat_user_id),
                                                    FOREIGN KEY (chat_group_id) REFERENCES CHAT_GROUP(id),
                                                    FOREIGN KEY (chat_user_id) REFERENCES CHAT_USER(id)
);

CREATE TABLE IF NOT EXISTS MESSAGE (
                                       id TEXT PRIMARY KEY,
                                       content TEXT NOT NULL,
                                       sender_id TEXT,
                                       recipient_id TEXT,
                                       group_id TEXT,
                                       chat_type TEXT NOT NULL,
                                       timestamp DATETIME NOT NULL,
                                       FOREIGN KEY (sender_id) REFERENCES CHAT_USER(id),
                                       FOREIGN KEY (recipient_id) REFERENCES CHAT_USER(id),
                                       FOREIGN KEY (group_id) REFERENCES CHAT_GROUP(id)
);