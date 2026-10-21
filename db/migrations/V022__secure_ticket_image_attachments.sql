CREATE TABLE IF NOT EXISTS ticket_attachments (
    id UUID PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    uploaded_by INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_name VARCHAR(180) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    content_base64 TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS ticket_attachments_ticket_id_idx ON ticket_attachments(ticket_id);
CREATE INDEX IF NOT EXISTS ticket_attachments_uploaded_by_idx ON ticket_attachments(uploaded_by);
