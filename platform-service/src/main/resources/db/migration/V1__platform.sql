-- 单实例平台业务：外键、唯一约束与行锁负责重启/并发后的真实性，不依赖JVM缓存。
CREATE TABLE users (
 id uuid PRIMARY KEY, username varchar(20) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL, role varchar(16) NOT NULL DEFAULT 'USER' CHECK(role IN ('USER','ADMIN')),
 disabled boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(username ~ '^[a-z0-9_]{3,20}$')
);
CREATE TABLE player_profiles (
 user_id uuid PRIMARY KEY REFERENCES users(id), display_name varchar(32) NOT NULL
);
CREATE TABLE login_sessions (
 token_hash char(64) PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
 expires_at timestamptz NOT NULL, last_seen timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user ON login_sessions(user_id);
CREATE TABLE friendships (
 low_id uuid NOT NULL REFERENCES users(id), high_id uuid NOT NULL REFERENCES users(id),
 requester uuid NOT NULL REFERENCES users(id), status varchar(12) NOT NULL CHECK(status IN ('PENDING','ACCEPTED')),
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(low_id,high_id), CHECK(low_id<high_id),
 CHECK(requester IN (low_id,high_id))
);
CREATE TABLE rooms (
 id varchar(40) PRIMARY KEY, game_id varchar(32) NOT NULL, game_version varchar(32) NOT NULL,
 rules_version varchar(32) NOT NULL, host_id uuid NOT NULL REFERENCES users(id), server_id varchar(40) NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','ALLOCATED','FINISHED','CLOSED')),
 match_id uuid, created_at timestamptz NOT NULL DEFAULT now(), touched_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE room_members (
 user_id uuid PRIMARY KEY REFERENCES users(id), room_id varchar(40) NOT NULL REFERENCES rooms(id),
 slot integer NOT NULL CHECK(slot BETWEEN 0 AND 1), ready boolean NOT NULL DEFAULT false,
 UNIQUE(room_id,slot)
);
CREATE TABLE room_invitations (
 room_id varchar(40) NOT NULL REFERENCES rooms(id), from_id uuid NOT NULL REFERENCES users(id),
 to_id uuid NOT NULL REFERENCES users(id), expires_at timestamptz NOT NULL,
 PRIMARY KEY(room_id,to_id), CHECK(from_id<>to_id)
);
CREATE TABLE match_records (
 id uuid PRIMARY KEY, room_id varchar(40) NOT NULL REFERENCES rooms(id), server_id varchar(40) NOT NULL,
 game_id varchar(32) NOT NULL, game_version varchar(32) NOT NULL, rules_version varchar(32) NOT NULL,
 seed bigint NOT NULL, status varchar(16) NOT NULL DEFAULT 'ALLOCATED' CHECK(status IN ('ALLOCATED','FINISHED','ABORTED')),
 outcome varchar(40) NOT NULL DEFAULT '', reason varchar(40) NOT NULL DEFAULT '', final_tick bigint NOT NULL DEFAULT 0,
 state_hash char(64), submission_hash char(64), created_at timestamptz NOT NULL DEFAULT now(), ended_at timestamptz
);
CREATE TABLE match_participants (
 match_id uuid NOT NULL REFERENCES match_records(id), user_id uuid NOT NULL REFERENCES users(id),
 slot integer NOT NULL CHECK(slot BETWEEN 0 AND 1), points integer NOT NULL DEFAULT 0,
 result varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(result IN ('PENDING','WIN','LOSS','DRAW','ABORTED')),
 PRIMARY KEY(match_id,user_id), UNIQUE(match_id,slot)
);
CREATE INDEX participant_user ON match_participants(user_id,match_id);
CREATE TABLE join_tickets (
 token_hash char(64) PRIMARY KEY, match_id uuid NOT NULL REFERENCES match_records(id),
 user_id uuid NOT NULL REFERENCES users(id), slot integer NOT NULL CHECK(slot BETWEEN 0 AND 1),
 expires_at timestamptz NOT NULL, consumed boolean NOT NULL DEFAULT false,
 connection_id varchar(64), UNIQUE(match_id,user_id)
);
CREATE TABLE replay_metadata (
 match_id uuid PRIMARY KEY REFERENCES match_records(id), reference varchar(160) NOT NULL,
 state_hash char(64) NOT NULL, expires_at timestamptz NOT NULL, available boolean NOT NULL DEFAULT false
);
