CREATE TABLE IF NOT EXISTS user_links (
    chat_id BIGINT,
    link_id BIGINT,
    tags TEXT,
    filters TEXT,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (chat_id, link_id),
    FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
    FOREIGN KEY (link_id) REFERENCES links(id) ON DELETE CASCADE
);
