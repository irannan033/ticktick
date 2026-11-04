-- GENERATED from backend/app/models.py (PostgreSQL dialect) by scripts/gen_schema.py. Do not edit by hand.
-- Times are epoch milliseconds (UTC). Primary keys are client-generated UUIDs.

CREATE TABLE users (
	id VARCHAR(36) NOT NULL, 
	google_sub VARCHAR(64) NOT NULL, 
	name VARCHAR(200) NOT NULL, 
	email VARCHAR(320), 
	timezone VARCHAR(64) NOT NULL, 
	whatsapp_number VARCHAR(20), 
	whatsapp_enabled BOOLEAN NOT NULL, 
	morning_time VARCHAR(5) NOT NULL, 
	evening_time VARCHAR(5) NOT NULL, 
	followup_hours INTEGER NOT NULL, 
	last_list TEXT NOT NULL, 
	pending_action TEXT, 
	last_inbound_at BIGINT NOT NULL, 
	last_morning_date VARCHAR(10) NOT NULL, 
	last_evening_date VARCHAR(10) NOT NULL, 
	last_followup_at BIGINT NOT NULL, 
	wa_fail_count INTEGER NOT NULL, 
	created_at BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	UNIQUE (whatsapp_number)
);

CREATE UNIQUE INDEX ix_users_google_sub ON users (google_sub);

CREATE TABLE categories (
	id VARCHAR(36) NOT NULL, 
	user_id VARCHAR(36) NOT NULL, 
	name VARCHAR(200) NOT NULL, 
	color_argb BIGINT NOT NULL, 
	updated_at BIGINT NOT NULL, 
	deleted_at BIGINT, 
	server_updated_at BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_categories_server_updated_at ON categories (server_updated_at);
CREATE INDEX ix_categories_user_id ON categories (user_id);

CREATE TABLE pomodoro_sessions (
	id VARCHAR(36) NOT NULL, 
	user_id VARCHAR(36) NOT NULL, 
	task_id VARCHAR(36), 
	planned_minutes INTEGER NOT NULL, 
	completed_seconds INTEGER NOT NULL, 
	started_at BIGINT NOT NULL, 
	ended_at BIGINT, 
	status VARCHAR(12) NOT NULL, 
	server_updated_at BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_pomodoro_sessions_server_updated_at ON pomodoro_sessions (server_updated_at);
CREATE INDEX ix_pomodoro_sessions_user_id ON pomodoro_sessions (user_id);

CREATE TABLE task_activity (
	id SERIAL NOT NULL, 
	user_id VARCHAR(36) NOT NULL, 
	task_id VARCHAR(36) NOT NULL, 
	action VARCHAR(40) NOT NULL, 
	note TEXT, 
	timestamp BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_task_activity_task_id ON task_activity (task_id);
CREATE INDEX ix_task_activity_user_id ON task_activity (user_id);

CREATE TABLE tasks (
	id VARCHAR(36) NOT NULL, 
	user_id VARCHAR(36) NOT NULL, 
	title VARCHAR(500) NOT NULL, 
	description TEXT NOT NULL, 
	priority VARCHAR(10) NOT NULL, 
	important BOOLEAN NOT NULL, 
	urgent BOOLEAN NOT NULL, 
	category_id VARCHAR(36), 
	start_at BIGINT, 
	due_at BIGINT, 
	reminder_at BIGINT, 
	estimated_minutes INTEGER, 
	status VARCHAR(12) NOT NULL, 
	completed_at BIGINT, 
	postpone_count INTEGER NOT NULL, 
	recurrence VARCHAR(10), 
	created_at BIGINT NOT NULL, 
	updated_at BIGINT NOT NULL, 
	deleted_at BIGINT, 
	server_updated_at BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_tasks_user_due ON tasks (user_id, due_at);
CREATE INDEX ix_tasks_user_id ON tasks (user_id);
CREATE INDEX ix_tasks_user_sync ON tasks (user_id, server_updated_at);

CREATE TABLE whatsapp_messages (
	id SERIAL NOT NULL, 
	user_id VARCHAR(36), 
	direction VARCHAR(3) NOT NULL, 
	content TEXT NOT NULL, 
	status VARCHAR(12) NOT NULL, 
	wa_message_id VARCHAR(128), 
	retry_count INTEGER NOT NULL, 
	timestamp BIGINT NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE, 
	UNIQUE (wa_message_id)
);

CREATE INDEX ix_whatsapp_messages_user_id ON whatsapp_messages (user_id);
