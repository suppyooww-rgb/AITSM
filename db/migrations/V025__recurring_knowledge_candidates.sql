CREATE TABLE IF NOT EXISTS knowledge_issue_patterns (
  id SERIAL PRIMARY KEY,
  category VARCHAR(100) NOT NULL,
  normalized_terms TEXT NOT NULL,
  representative_title VARCHAR(200) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS knowledge_issue_patterns_category_idx
  ON knowledge_issue_patterns(category);

CREATE TABLE IF NOT EXISTS knowledge_issue_occurrences (
  id BIGSERIAL PRIMARY KEY,
  pattern_id INT NOT NULL REFERENCES knowledge_issue_patterns(id) ON DELETE CASCADE,
  ticket_id INT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
  resolution TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(ticket_id)
);
CREATE INDEX IF NOT EXISTS knowledge_issue_occurrences_pattern_idx
  ON knowledge_issue_occurrences(pattern_id);

CREATE TABLE IF NOT EXISTS knowledge_candidates (
  id SERIAL PRIMARY KEY,
  pattern_id INT NOT NULL UNIQUE REFERENCES knowledge_issue_patterns(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  category VARCHAR(100) NOT NULL,
  occurrence_count INT NOT NULL CHECK (occurrence_count >= 3),
  status VARCHAR(20) NOT NULL DEFAULT 'READY',
  published_article_id INT REFERENCES knowledge_articles(id) ON DELETE SET NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  reviewed_at TIMESTAMP,
  reviewed_by INT REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS knowledge_candidates_status_idx
  ON knowledge_candidates(status, updated_at);
