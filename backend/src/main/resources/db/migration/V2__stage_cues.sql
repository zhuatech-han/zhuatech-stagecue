-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE production (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE cue_book (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 production_id bigint NOT NULL,
 revision int NOT NULL,
 title varchar(120) NOT NULL,
 status varchar(30) NOT NULL,
 created_by bigint NOT NULL,
 approved_by bigint,
 content_hash varchar(64),
 created_at timestamp(6) NOT NULL,
 approved_at timestamp(6),
 version bigint NOT NULL
);

CREATE TABLE book_editor (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 book_id bigint NOT NULL,
 actor_id bigint NOT NULL
);

CREATE TABLE cue_definition (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 book_id bigint NOT NULL,
 cue_sequence int NOT NULL,
 code varchar(60) NOT NULL,
 kind varchar(60) NOT NULL,
 trigger_text varchar(300) NOT NULL,
 description varchar(1000) NOT NULL,
 is_optional boolean NOT NULL,
 enabled boolean NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE show_run (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL,
 book_id bigint NOT NULL,
 production_id bigint NOT NULL,
 department_id bigint NOT NULL,
 created_by bigint NOT NULL,
 caller_id bigint NOT NULL,
 supervisor_id bigint NOT NULL,
 production_name varchar(120) NOT NULL,
 book_title varchar(120) NOT NULL,
 book_revision int NOT NULL,
 book_hash varchar(64) NOT NULL,
 location varchar(200) NOT NULL,
 planned_at timestamp(6) NOT NULL,
 mode varchar(30) NOT NULL,
 status varchar(30) NOT NULL,
 outcome varchar(30),
 started_at timestamp(6),
 ended_at timestamp(6),
 closed_at timestamp(6),
 version bigint NOT NULL
);

CREATE TABLE cue_execution (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 run_id bigint NOT NULL,
 source_cue_id bigint NOT NULL,
 cue_sequence int NOT NULL,
 code varchar(60) NOT NULL,
 kind varchar(60) NOT NULL,
 trigger_text varchar(300) NOT NULL,
 description varchar(1000) NOT NULL,
 is_optional boolean NOT NULL,
 operator_id bigint,
 received_at timestamp(6),
 status varchar(30) NOT NULL,
 standby_at timestamp(6),
 ready_at timestamp(6),
 called_at timestamp(6),
 done_at timestamp(6),
 note varchar(1000) NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE run_hold (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 run_id bigint NOT NULL,
 raised_by bigint NOT NULL,
 resolved_by bigint,
 reason varchar(1000) NOT NULL,
 resolution varchar(1000),
 raised_at timestamp(6) NOT NULL,
 resolved_at timestamp(6),
 status varchar(30) NOT NULL,
 version bigint NOT NULL
);

CREATE TABLE command_record (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 request_key varchar(36) NOT NULL,
 fingerprint varchar(64) NOT NULL,
 response_json longtext NOT NULL
);

CREATE TABLE business_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 object_type varchar(30) NOT NULL,
 object_id bigint NOT NULL,
 actor_id bigint NOT NULL,
 action varchar(60) NOT NULL,
 note varchar(1000) NOT NULL,
 snapshot longtext NOT NULL,
 created_at timestamp(6) NOT NULL
);

ALTER TABLE production ADD UNIQUE(reference);
ALTER TABLE production ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE production ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE production ADD CHECK(version>=1);
ALTER TABLE cue_book ADD UNIQUE(production_id,revision);
ALTER TABLE cue_book ADD FOREIGN KEY(production_id) REFERENCES production(id);
ALTER TABLE cue_book ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE cue_book ADD FOREIGN KEY(approved_by) REFERENCES account(id);
ALTER TABLE cue_book ADD CHECK(revision>=1 AND version>=1);
ALTER TABLE book_editor ADD UNIQUE(book_id,actor_id);
ALTER TABLE book_editor ADD FOREIGN KEY(book_id) REFERENCES cue_book(id);
ALTER TABLE book_editor ADD FOREIGN KEY(actor_id) REFERENCES account(id);
ALTER TABLE cue_definition ADD UNIQUE(book_id,cue_sequence);
ALTER TABLE cue_definition ADD UNIQUE(book_id,code);
ALTER TABLE cue_definition ADD FOREIGN KEY(book_id) REFERENCES cue_book(id);
ALTER TABLE cue_definition ADD CHECK(cue_sequence BETWEEN 1 AND 9999 AND version>=1);
ALTER TABLE show_run ADD UNIQUE(reference);
ALTER TABLE show_run ADD FOREIGN KEY(book_id) REFERENCES cue_book(id);
ALTER TABLE show_run ADD FOREIGN KEY(production_id) REFERENCES production(id);
ALTER TABLE show_run ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE show_run ADD FOREIGN KEY(created_by) REFERENCES account(id);
ALTER TABLE show_run ADD FOREIGN KEY(caller_id) REFERENCES account(id);
ALTER TABLE show_run ADD FOREIGN KEY(supervisor_id) REFERENCES account(id);
ALTER TABLE show_run ADD CHECK(caller_id<>supervisor_id AND version>=1);
ALTER TABLE cue_execution ADD UNIQUE(run_id,cue_sequence);
ALTER TABLE cue_execution ADD FOREIGN KEY(run_id) REFERENCES show_run(id);
ALTER TABLE cue_execution ADD FOREIGN KEY(source_cue_id) REFERENCES cue_definition(id);
ALTER TABLE cue_execution ADD FOREIGN KEY(operator_id) REFERENCES account(id);
ALTER TABLE cue_execution ADD CHECK(version>=1);
ALTER TABLE run_hold ADD FOREIGN KEY(run_id) REFERENCES show_run(id);
ALTER TABLE run_hold ADD FOREIGN KEY(raised_by) REFERENCES account(id);
ALTER TABLE run_hold ADD FOREIGN KEY(resolved_by) REFERENCES account(id);
ALTER TABLE run_hold ADD CHECK(version>=1);
ALTER TABLE command_record ADD UNIQUE(request_key);
ALTER TABLE business_event ADD FOREIGN KEY(actor_id) REFERENCES account(id);
CREATE INDEX ix_run_scope ON show_run(department_id,created_by);
CREATE INDEX ix_exec_operator ON cue_execution(operator_id,run_id);
CREATE INDEX ix_event_object ON business_event(object_type,object_id,id);
CREATE INDEX ix_hold_run ON run_hold(run_id,status);
