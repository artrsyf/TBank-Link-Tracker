CREATE TABLE IF NOT EXISTS UserLink (
    chatId BIGINT,
    linkId BIGINT,
    tags TEXT,
    filters TEXT,
    createdAt TIMESTAMP NOT NULL,
    PRIMARY KEY (chatId, linkId),
    FOREIGN KEY (chatId) REFERENCES Chat(chatId) ON DELETE CASCADE,
    FOREIGN KEY (linkId) REFERENCES Link(id) ON DELETE CASCADE
);
