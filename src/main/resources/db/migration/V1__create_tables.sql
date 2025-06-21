CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username VARCHAR(255),
    training_counter INT NOT NULL DEFAULT 0
);

CREATE TABLE topics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    max_difficulty_in_topic DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    type VARCHAR(50) NOT NULL,
    order_index INT,
    CONSTRAINT topics_type_check_initial CHECK (type IN ('OGE', 'EGE', 'TEST'))
);

CREATE TABLE tests (
    id BIGSERIAL PRIMARY KEY,
    start_id INT UNIQUE NOT NULL,
    advice TEXT
);

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    test_id BIGINT REFERENCES tests(id),
    content TEXT NOT NULL,
    answer TEXT,
    difficulty DOUBLE PRECISION NOT NULL
);

CREATE TABLE user_topic_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    correctly_solved_count INT NOT NULL DEFAULT 0,
    correctly_solved_hard_count INT NOT NULL DEFAULT 0,
    training_stage_index INT NOT NULL DEFAULT 0,
    next_training_number INT,
    UNIQUE (user_id, topic_id)
);

CREATE TABLE user_task_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    is_correct BOOLEAN NOT NULL,
    attempt_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempted_at_training_counter INT NOT NULL,
    next_review_at_training INT
);

CREATE TABLE magnets (
    id BIGSERIAL PRIMARY KEY,
    start_id INT UNIQUE NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    message TEXT
);
