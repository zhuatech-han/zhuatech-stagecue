[中文](README.md) | [English](README.en.md)

<div align="center">
<img src="frontend/public/brand/logo.jpg" alt="ZhiHua Technology logo" width="170" />

# StageCue · Stage Cue Books and Performance Execution Records

**Public source for learning / non-commercial use**

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

[User manual](docs/操作手册.md) · [Deployment](docs/部署说明.md) · [API reference](docs/接口说明.md) · [Architecture](docs/架构说明.md)
</div>

## Cue books, crew acknowledgment and show records

StageCue uses Java 21 / Spring Boot, Vue 3, MySQL and Flyway for stage cue revisions, independent approval, assigned crew and execution evidence. It serves theaters, production teams and rehearsals. Each cue has a type, reference, sequence, dialogue/action trigger, instructions and mandatory/optional status. Approved revisions freeze; each show copies its own cue snapshot, so later revisions cannot silently rewrite a show in progress. The linked detailed manuals are currently in Chinese.

Callers and operators separately record standby, ready, call and completion. Assigned crew can request a hold; the independent supervisor records its resolution and the caller records resumption. Actions retain versions, actors, explanations, software timestamps and evidence snapshots.

**This is a manual business record system.** Timestamps indicate when actions were recorded in software. It does not control lighting, audio, video, machinery, pyrotechnics or stage equipment, provide live intercom/real-time cue signals, play media automatically or implement safety interlocks. Use appropriate professional facilities for on-site communications, control and safety procedures.

### Business and administrative roles

| Role | Operations |
|---|---|
| Production coordinator | Productions, cue book revisions, show planning and operator assignment |
| Independent supervisor | Approve revisions they did not edit; resolve holds and independently review assigned shows |
| Caller | Start assigned shows, standby/call records, skip optional cues, hold/resume, finish and abort |
| Technical operator | Personally acknowledge assigned cues, confirm readiness/completion and request holds in their shows |
| Administrator | Productions/directories, accounts, roles, permissions, departments, navigation, types and settings; business actions still require assignments and independent review |

### Implemented modules

- **Productions:** reference, name, responsible department and enabled state. Reference and department remain fixed after creation; history is retained.
- **Cue book revisions:** unique sequence/reference within a revision; up to 200 LIGHT, SOUND, STAGE or VIDEO cues; include/exclude and mandatory/optional flags; submit, independently approve, return, withdraw and cancel.
- **Revision freezing:** approval records a cue digest, reviewer and timestamp. Anyone who edited that revision cannot approve it. A new draft copies enabled cues from the latest approved revision; old approved versions remain available as history.
- **Show snapshots:** the latest approved book at creation supplies production/book names, revision, digest and all enabled cues. REHEARSAL/PERFORMANCE, location, planned time, caller and independent supervisor are recorded.
- **Crew preparation:** assign an enabled, authorized operator to each cue; the operator personally acknowledges. Reassignment clears that cue's acknowledgment; editing a preparing show clears all acknowledgments. Starting rechecks account status and permissions.
- **Sequential execution:** process the first incomplete cue only: caller standby → assigned operator ready → caller call → operator done. Mandatory cues cannot be skipped; optional skips require a reason before calling.
- **Holds and resumption:** assigned members request holds during running shows. New progression is blocked; already-called cues may still report completion facts. The assigned supervisor resolves all open holds, then the caller resumes; uncalled standby/ready cues return to pending.
- **Finish and review:** finish after every cue is completed or validly skipped. Aborting requires no called-but-unreported cues and no open holds. Finished and aborted outcomes remain distinct after independent supervisor review. Preparing shows can be canceled.
- **Authorization and evidence:** 15 interface permissions, ALL/department/SELF scopes plus explicit assignments, five built-in roles and 12 navigation entries. Live authorization, version conflicts and UUID exact retries protect writes. Detail and JSON exports use the same scope; no advertisements are inserted into business exports.
- **Administration:** authorized show/cue counts and state distributions; enabled accounts, password resets/own password changes, roles, departments, permission descriptions, navigation, cue type names, settings, audit, Chinese/English and narrow-screen pages.

**Not implemented:** ticketing, memberships/courses, performer attendance, venue collision checks, inventory, equipment integration, media files/playback, live intercom, automatic cues, offline execution, real-time push, parallel cues/dependency graphs, revision or crew changes after starting, failed-cue retries/actual-time corrections, attachments, notifications, multi-tenancy, external authentication and AI. No mandatory external account, simulated business interface or seeded business demo records are provided.

## Actual running pages

Screenshots come from an independent test database. TEST records are acceptance inputs and are not created during an empty-database installation.

| Login | Operator workspace |
|---|---|
| ![Login](docs/screenshots/login.jpg) | ![Operator workspace](docs/screenshots/operator-home.jpg) |

Login: session authentication into the workspace. Operator workspace: personally assigned shows and cues.

| Cue book revision | Show execution |
|---|---|
| ![Cue book revision](docs/screenshots/book.jpg) | ![Show execution](docs/screenshots/run.jpg) |

Cue book revision: ordered cues and independent approval. Show execution: frozen snapshots and records by assigned crew.

| Accounts | Roles and permissions |
|---|---|
| ![Accounts](docs/screenshots/users.jpg) | ![Roles and permissions](docs/screenshots/roles.jpg) |

Accounts: enabled status, departments and roles. Roles: interface permissions and data scopes.

| Show statistics | System settings |
|---|---|
| ![Show statistics](docs/screenshots/dashboard.jpg) | ![System settings](docs/screenshots/settings.jpg) |

Statistics: authorized show states and cue counts. Settings: supported name/capacity configuration.

![Mobile interface](docs/screenshots/mobile.jpg)

Mobile interface: narrow-screen cue records.

![English interface](docs/screenshots/english.jpg)

English interface: English operation pages.

## States and constraints

Cue book: draft/returned → submitted → approved. The submitter may withdraw; independent reviewers may return; unapproved revisions may be canceled. A production has at most one draft, returned or submitted revision. Approved text/cues are immutable. Superseded approved books cannot create new shows; existing shows retain their snapshots.

Show: preparing → running ⇄ held → awaiting review → reviewed. Aborts enter a separate review path and remain aborted after review. Preparing shows may be canceled. Caller and supervisor must differ, and each operator must differ from both.

Cue: pending → standby → ready → called → done. Optional cues can be skipped before calling. Prior cues must be completed or validly skipped; this release is strictly serial. After resumption, uncalled cues require new standby/readiness confirmations; prior call/completion facts remain.

Server fact timestamps use UTC microseconds; pages display Shanghai time. Planned time is manually supplied, from `1970-01-01T00:00:01Z` through `2038-01-19T03:14:07.999999Z`, stored at microsecond precision. Buttons assist users; the backend rechecks authorization, versions and constraints.

## Architecture and directory structure

Browser → same-origin Nginx → Vue / Spring Boot API → MySQL. Identity, cue revisions, acknowledgment, execution, holds and evidence are persisted. Flyway migrates the schema; JPA validates it rather than recreating it. Sessions use HttpOnly cookies and writes require CSRF.

| Layer | Pinned version/configuration |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Security, JPA and Flyway |
| Database | MySQL 8.4, MariaDB JDBC 3.5.10 and UTC storage |
| Frontend | Vue 3.5.40, Vite 8.1.5, Node 24.19.0+, npm 11 and Lucide 1.48.0 |
| Checks | Spotless 2.43.0 / Google Java Format 1.24, ESLint 10.11, Prettier 3.9.9, JUnit HTTP/JPA and Node tests |
| Deployment | Compose v2, Nginx 1.29, non-root applications and default loopback port 8129 |

```text
backend/src/main/java/cn/zhuatech/stagecue/   Cues, shows, authorization and administration
backend/src/main/resources/db/migration/    V1 identity and V2 stage cues
backend/src/test/                           HTTP/JPA and timestamp tests
frontend/src/                              Bilingual pages, actions and input constraints
frontend/public/brand/                     Original logo and Chinese contact assets
scripts/                                   Initialization, actual HTTP acceptance and release checks
docs/                                      Operations, API, deployment, architecture, screenshots and notices
compose.yaml                               Three services and persistent MySQL volume
```

Business tables are `production`, `cue_book`, `book_editor`, `cue_definition`, `show_run`, `cue_execution`, `run_hold`, `command_record` and `business_event`. Identity tables cover accounts, departments, roles/permission relations, navigation, dictionaries, settings and audit. Foreign keys, unique indexes and versions protect identity. See [Architecture and database](docs/架构说明.md).

## Requirements and installation

Docker Desktop / Docker Engine, Compose v2, Python 3 and access to official images/public dependencies are required. Source development additionally needs Java 21 / Maven 3.9, Node 24.19.0+ / npm 11 and MySQL 8.4.

### Docker Compose

```sh
python3 scripts/init-env.py
docker compose -p stagecue config --quiet
docker compose -p stagecue up -d --build --wait
```

Open **[http://127.0.0.1:8129/](http://127.0.0.1:8129/)** with username `admin` and the local ignored `.env` value `ADMIN_PASSWORD`. The initialization script creates independent random passwords with mode 0600, never prints them and refuses to overwrite configuration. If configuration already exists, start directly.

To use a different host port:

```sh
WEB_PORT=18129 docker compose -p stagecue up -d --build --wait
```

Leave other projects' services running. Containers use service names, not a hardcoded localhost API. MySQL has no host port; services have health waiting, a named volume and restart policies.

### Source development

Provide a separate local backend connection through `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG`, `DATABASE_PASSWORD` and `ADMIN_PASSWORD`. The frontend development proxy uses backend port 8080; see `frontend/vite.config.js`.

```sh
mvn -B -f backend/pom.xml spring-boot:run
```

In another terminal at the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Do not write passwords into command history. Inject them through controlled environment configuration. Host source execution does not automatically load `.env`; Compose does. The development frontend normally runs at `http://127.0.0.1:5173`; Compose does not expose its database to the host by default.

## Configuration and database initialization

| Name | Purpose |
|---|---|
| `DATABASE_PASSWORD` | Independent Compose application database password |
| `MYSQL_ROOT_PASSWORD` | MySQL initialization and controlled backup administration password |
| `ADMIN_PASSWORD` | Strong administrator password used only for an empty database |
| `WEB_PORT` | Host web port, default 8129 |
| `BIND_ADDRESS` | Default 127.0.0.1; external exposure requires the deployment's security policy |
| `COOKIE_SECURE` | Local HTTP: false; HTTPS deployment: true |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_CATALOG` | Backend-process overrides for source development/external databases; local Compose needs no added values |

[.env.example](.env.example) lists names only; never commit real configuration. An empty database initializes headquarters, `admin`, five roles, 15 permissions, 12 navigation entries, four cue types and three settings. The private `ADMIN_PASSWORD` is stored with BCrypt cost 12; there is no public fixed password. Business tables start empty. Restarts do not reinitialize existing accounts or business facts.

[http://127.0.0.1:8129/actuator/health](http://127.0.0.1:8129/actuator/health) exposes health status only, normally including `"status":"UP"`. The named MySQL volume persists data. Do not delete actual business volumes or rewrite applied migrations.

## Testing

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p stagecue config --quiet
git diff --check
python3 scripts/release-check.py
```

There are 41 backend tests (35 HTTP/JPA and six timestamp precision/range tests) and ten frontend tests. Integration tests use isolated H2 and a dynamically generated random password. They cover complete workflows, independent approval, old snapshots, assignments, acknowledgment, sequencing, holds/resumption, aborts, retries, concurrency, scopes, immediate disablement, CSRF and last-administrator protection. Backend Docker builds run all tests. H2 does not replace actual MySQL acceptance.

After starting an **independent, disposable test project/database**:

```sh
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
python3 scripts/smoke.py --verify
```

Only explicit test-write mode creates TEST records and random accounts. Capture saves private comparison state in ignored `output/qa-state.json`; do not publish it. After page acceptance, capture again before restarting/restoring. Verify reauthenticates every role and compares saved responses; `TEST_URL` selects a separately restored environment.

## Deployment, upgrades and recovery

See [Deployment and recovery](docs/部署说明.md). Before upgrading, pause business writes, retain the application image version and back up the complete schema, Flyway history, identity, business and evidence. Backups contain password hashes and unpublished production information; keep them restricted outside the repository, with mode 0600. Reference authorized container environment variables for backup tools rather than typing or printing passwords.

Restore into a new Compose project, separate MySQL volume and distinct web port using matching application images. Start MySQL, import the full backup, then start backend and frontend. Check health, Flyway success, tables, every role's login, book digests, show snapshots, assignments, execution and hold events. Compare private acceptance state with `--verify` before any deployment-side switch.

Only append trusted migrations; do not edit applied V1/V2 or delete tables/volumes to handle failure. Verify migration compatibility and restoration before reverting application versions. External deployment requires HTTPS, `COOKIE_SECURE=true`, trusted proxies, network isolation, least privilege, restricted secrets, retained backups and appropriate monitoring. The local Compose network is not a complete external database security scheme.

`docker compose -p stagecue stop` retains data. `down` removes that project's containers/network and retains its volume. **Use volume removal only for explicitly disposable test resources after checking project labels.**

## Known limits and troubleshooting

This is a bounded, single-organization learning implementation: productions, cue books and shows each have at most 1,000 records; `maxRecords` is configurable from 100 to 1,000. Each book has at most 200 cues and pages at most 100 entries. Lists authorize before bounded sorting/pagination. A fixed organization lock serializes writes. Large concurrency, multiple instances, high availability, cross-organization isolation and live stage performance guarantees have not been validated.

Accounts, roles, scopes and password hashes are rechecked on every request. Password changes invalidate old sessions; login has application rate limiting. An operator with ALL scope still sees only their assigned cues and associated shows. Administrator status does not replace assignment. See [SECURITY](SECURITY.md).

| Symptom | Check |
|---|---|
| Unhealthy services | This project's MySQL/backend logs, configuration and migrations; preserve the original volume |
| Show cannot start | Every cue's operator assignment, personal acknowledgment, enabled account and permission |
| Cue action unavailable | Assigned role, prior completion, standby/readiness and show state |
| Cannot resume | Assigned supervisor resolves all open holds before the caller resumes |
| Cannot approve book | Use an independent account that never edited that revision |
| Cannot finish/abort | Finish cues; before abort, report every called cue and resolve holds |
| Old book rejected for a new show | Use the latest approved version; existing snapshots retain their versions |
| Changing `ADMIN_PASSWORD` does not alter an existing account | It is initialization-only; use own password change or an authorized reset |
| Migration validation failure | Apply trusted new migrations; do not rewrite history or delete actual business volumes |

## License, contributions and contact

The project's own code uses [ZhuaTech Non-Commercial Source License 1.0](LICENSE): personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** Enterprise private deployment, paid delivery/services, resale and in-depth customization require separate written authorization. Preserve attribution, website, copyright, license and licensing contacts. This is publicly readable non-commercial source, not an OSI-approved license. Third-party dependencies retain their licenses; see [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md). The software is provided as is, with no unverified production-readiness claim.

See [CONTRIBUTING](CONTRIBUTING.md) for redacted reproducible feedback. Do not publish credentials, personal information or unpublished production materials in issues. Report security concerns privately through the contacts below.

For commercial licensing, in-depth custom development, private deployment or system integration, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
