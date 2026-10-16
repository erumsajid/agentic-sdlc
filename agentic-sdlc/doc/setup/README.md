# Local Setup Guide

## Required software

| Software | Version | Why it is needed |
| --- | --- | --- |
| Java Development Kit | 21 | Compile and run the Spring Boot modules |
| Apache Maven | 3.9 or newer | Build the multi-module project and run tests/services |
| PostgreSQL Server | 14 or newer | Persist workflow records, artifacts, links, and click analytics |
| PostgreSQL client tools or pgAdmin | Same major version as server is recommended | Create and inspect the database |
| Postman Desktop or Postman web | Current version | Optional: import and run the API collections |

Maven resolves the Java libraries from the `pom.xml` files; do not install these libraries separately. Main dependencies include Spring Boot Web, Data JPA, Actuator, Micrometer Prometheus, PostgreSQL JDBC, Flyway, Jackson YAML, and JGraphT. Tests use Spring Boot Test, JUnit 5, Mockito, and AssertJ.

## Install/check the tools

Install a JDK 21 distribution, Maven 3.9+, and PostgreSQL for your operating system. Open a new PowerShell terminal and check:

```powershell
java -version
javac -version
mvn -version
psql --version
```

If `psql` or `createdb` is not on `PATH` on Windows, run them from the PostgreSQL `bin` directory, commonly `C:\Program Files\PostgreSQL\<version>\bin`.

## Create the database

Start the PostgreSQL service. Create the database named `orchestrator` once, using a PostgreSQL superuser or a role with `CREATEDB` permission. Flyway requires the database to exist before the applications connect; after connecting, it creates the application tables and the shortener's `URL_SHORTENER` schema automatically.

### PowerShell with PostgreSQL command-line tools

```powershell
createdb -h localhost -p 5432 -U postgres orchestrator
```

`createdb` prompts for the PostgreSQL role password. If the database already exists, do not drop it; skip the first command and continue. Verify it with:

```powershell
psql -h localhost -p 5432 -U postgres -d orchestrator -c "SELECT current_database();"
```

The URL shortener's schema and tables appear after the shortener service starts and Flyway runs its migrations. PostgreSQL stores the unquoted schema name `URL_SHORTENER` in lowercase as `url_shortener`.

### pgAdmin alternative

1. Connect to the local PostgreSQL server.
2. Right-click **Databases** → **Create** → **Database**; enter `orchestrator` and save.
3. Start the services; Flyway creates the shortener schema and migration tables automatically.

## Configure database credentials

The orchestrator connects to `localhost:5432/orchestrator` as `postgres` with the local default password `root`. For a different role or password, set environment variables in the same terminal where the app will run:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/orchestrator"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "<your-postgres-password>"
```

The URL shortener uses the same database and defaults to the same local account. Its settings can be overridden separately:

```powershell
$env:SHORTENER_DB_URL = "jdbc:postgresql://localhost:5432/orchestrator?currentSchema=URL_SHORTENER"
$env:SHORTENER_DB_USER = "postgres"
$env:SHORTENER_DB_PASSWORD = "<your-postgres-password>"
```

Do not commit passwords or API keys to the repository. The shortener schema should be owned by, or writable by, the configured application database role so Flyway can create its migration history and tables.

## Build and run

From the repository root (the directory containing the root `pom.xml`):

```powershell
mvn test
mvn -pl orchestrator spring-boot:run
```

Run the shortener in a second terminal:

```powershell
mvn -pl shortener-service spring-boot:run
```

Flyway creates orchestrator tables in the default schema and the shortener schema, tables, and separate Flyway history table in `URL_SHORTENER`. Check service health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
curl.exe -i http://localhost:8081/api/urls
```

The shortener request returns HTTP 405 because `GET /api/urls` is not a functional route; it only confirms the process accepts HTTP. Use the Postman collection to validate functional endpoints.

## Postman collections

Import these JSON collection files in Postman (**Import** → **File**):

- Workflow and steps 1–5: [`doc/agentic-sdlc-steps-1-5.postman_collection.json`](../agentic-sdlc-steps-1-5.postman_collection.json), base URL `http://localhost:8080`.
- URL shortener: [`doc/url-shortener.postman_collection.json`](../url-shortener.postman_collection.json), base URL `http://localhost:8081`.

Start the corresponding service before running its collection. For workflow requests, run the sequential approval/retry/control requests as described in their request descriptions. For shortener requests, run in order: create a link, redirect, analytics, duplicate alias, invalid target.

## Common setup issues

| Symptom | Check |
| --- | --- |
| Connection refused on port 5432 | PostgreSQL service is running and listening on the configured port |
| Password authentication failed | `DB_USER`/`DB_PASSWORD` and `SHORTENER_DB_USER`/`SHORTENER_DB_PASSWORD` match the PostgreSQL role |
| Database does not exist | Run the `createdb` command once, or create it in pgAdmin |
| Schema permission error | Ensure the configured role owns `URL_SHORTENER` or has schema `USAGE` and `CREATE` privileges |
| Port 8080 or 8081 already in use | Stop the other process or change the respective `server.port` / `SHORTENER_PORT` setting |
| Maven command not found | Install Maven 3.9+ and open a new terminal so `PATH` is refreshed |
