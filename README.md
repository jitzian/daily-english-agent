# 📚 Daily English Vocabulary Agent

> **Why I Built This:** After downloading *Vocabulary Builder*, *WordUp*, *Memrise*, and approximately 47 other "premium" vocabulary apps from the Play Store — all of which wanted $9.99/month just to tell me what "perspicacious" means — I had an epiphany. 💡 I'm a developer. I have a computer. I have an irrational attachment to my money. Why am I paying for something I can build myself in a weekend fueled by coffee and spite? So here we are: a fully automated, AI-powered, Discord-posting, PostgreSQL-backed vocabulary teacher that cost me $0/month and 100% of my dignity when I explained this project to my friends. 🎯

---

## 🎯 What Does This Do?

A **Spring Boot + Ktor** microservice that uses **Ollama's llama3.2:latest** model to:
- 🤖 Generate a unique English vocabulary word every day at **7 AM EST**
- 📢 Post it automatically to your **Discord channel** 
- 🗄️ Store every word in **PostgreSQL** (no repeats, ever)
- 🌐 Serve the word via a **REST API** (`curl`-able!)
- 🐳 Run everything in **Docker** with one command
- 💪 Handle failures gracefully (VPN? No problem. Timeout? Cached word ready.)

---

## Features

- 🤖 **AI-Powered Vocabulary Generation** — Ollama `llama3.2:latest` via HTTP API
- 📅 **Scheduled Daily Fetching** — Spring `@Scheduled` cron at **7 AM EST** daily
- 📢 **Discord Integration** — Posts the word of the day to a configured channel
- 🔄 **Retry Logic** — 5 attempts with 10-second delays; graceful error fallback
- 🗄️ **PostgreSQL Persistence** — Tracks all historical words to ensure uniqueness
- 💾 **Cached Word Fallback** — Returns last valid word + error description on failure
- 🏥 **Health Monitoring** — Spring Actuator health checks for DB and Ollama
- 🌐 **Dual Server** — Spring Boot on **8090** (management), Ktor on **8091** (API)
- ⚡ **Kotlin Coroutines** — Async operations with `Dispatchers.IO`
- 🐳 **Fully Containerized** — Single `docker compose up -d --build` deploys everything

---

## Prerequisites

| Requirement | Notes |
|---|---|
| **Docker Desktop** | Runs the app + PostgreSQL containers |
| **Ollama on host** | Must be running on the host machine at port `11434` |
| **llama3.2:latest** | Auto-validated on startup; pulled if missing |
| **`.env` file** | Discord credentials (see below) |

> **VPN Note (NordVPN):** Docker uses `host.docker.internal` which is resolved by the Docker daemon, bypassing VPN DNS — Ollama remains reachable even when the VPN is active.

---

## Quick Start (Docker — recommended)

### 1. Ensure Ollama is running on the host

```bash
curl http://localhost:11434/
# Expected: Ollama is running

ollama list
# Verify llama3.2:latest is present (app will pull it automatically if missing)
```

### 2. Create the `.env` file at the project root

```bash
cp .env.example .env
# Fill in your Discord credentials inside .env
```

`.env` format:
```env
DISCORD_BOT_TOKEN=your-discord-bot-token
DISCORD_CHANNEL_ID=your-discord-channel-id
```

### 3. Build and start all containers

```bash
docker compose up -d --build
```

This starts:
- `vocabulary_db` — PostgreSQL 16 on port `5432`
- `daily-english-agent` — Spring Boot + Ktor on ports `8090` / `8091`

### 4. Follow logs

```bash
docker compose logs -f app
```

### 5. Test the API

```bash
curl http://localhost:8091/word-of-the-day
```

---

## Running Locally (without Docker)

Requires a running PostgreSQL instance and Ollama on the host.

```bash
# Start only the database
docker compose up -d db

# Run the app (set timezone so the 7 AM cron fires correctly)
export TZ="America/New_York" && ./gradlew bootRun
```

---

## Docker Commands Reference

```bash
# First run / after any code changes
docker compose up -d --build

# Subsequent runs (no code changes)
docker compose up -d

# Follow app logs
docker compose logs -f app

# Stop all containers
docker compose down

# Stop and wipe the database volume
docker compose down -v

# Restart only the app
docker compose restart app

# Query the database directly
docker exec vocabulary_db psql -U regular_user -d englishWordsDB \
  -c "SELECT word, part_of_speech, is_active, fetched_at FROM words_history ORDER BY fetched_at DESC LIMIT 10;"
```

---

## Discord Setup

1. **Create a Discord Bot:**
   - Go to the [Discord Developer Portal](https://discord.com/developers/applications).
   - Click "New Application" and name your bot.
   - Navigate to the "Bot" tab and click "Add Bot."
   - Copy the **Bot Token** and save it in your `.env` file.

2. **Invite the Bot to Your Server:**
   - Go to the "OAuth2" tab and select "URL Generator."
   - Under "Scopes," select `bot`.
   - Under "Bot Permissions," select `Send Messages`.
   - Copy the generated URL and open it in your browser to invite the bot to your server.

3. **Get Your Channel ID:**
   - Enable "Developer Mode" in Discord settings.
   - Right-click your desired channel and select "Copy ID."
   - Save this ID in your `.env` file as `DISCORD_CHANNEL_ID`.

---

## Ollama Setup

1. **Install Ollama:**
   - Follow the [Ollama Installation Guide](https://ollama.ai/docs/installation).

2. **Pull the Model:**
   ```bash
   ollama pull llama3.2:latest
   ```

3. **Verify Installation:**
   ```bash
   curl http://localhost:11434/
   # Expected: Ollama is running
   ```

---

## Troubleshooting

- **VPN Issues:** Ensure `host.docker.internal` resolves correctly. Use `docker network inspect bridge` to verify.
- **Timeouts:** Increase `request_timeout` in `application.properties` if needed.
- **Database Errors:** Ensure `vocabulary_db` container is running and accessible.

---

## License

MIT License. See `LICENSE` file for details.

---

**Made with ☕ and 🧠 using Spring Boot, Ktor, Ollama, and Discord4J**


## Features

- 🤖 **AI-Powered Vocabulary Generation**: Uses Ollama's llama3.3:latest model via Koog agents
- 📅 **Scheduled Daily Fetching**: Automatically fetches new words at 7 AM EST daily
- 🔄 **Retry Logic**: 5 attempts with 120-second delays for reliable operation
- 🗄️ **PostgreSQL Persistence**: Tracks all historical words to ensure uniqueness
- 🚫 **Duplicate Prevention**: Application-level validation before calling AI model
- 💾 **Cached Word Fallback**: Returns last valid word with error description on failures
- 🏥 **Health Monitoring**: Spring Actuator health checks for Ollama and database
- 🌐 **Dual Server Setup**: Spring Boot (8090) for management, Ktor (8091) for API
- ⚡ **Kotlin Coroutines**: Async operations with Dispatchers.IO for optimal performance

## Prerequisites

- **JDK 21**: Java Development Kit 21 or higher
- **Docker & Docker Compose**: For PostgreSQL database container
- **Ollama**: Local installation of Ollama (model auto-pulls on startup)
- **Kotlin**: Included via Gradle

## Quick Start

### 1. Start PostgreSQL Database

```bash
cd docker
docker-compose up -d
```

This starts the PostgreSQL container with:
- Container name: `vocabulary_db`
- Database: `englishWordsDB`
- User: `regular_user`
- Password: `this_is_a_password`
- Port: `5432`

### 2. Verify Ollama is Running

Ensure Ollama is installed and running:

```bash
# Check if Ollama is running
ollama list

# If not installed, visit: https://ollama.ai
```

The application will automatically pull `llama3.3:latest` on startup if not present.

### 3. Run the Application

```bash
./gradlew bootRun
```

Or on Windows:

```bash
gradlew.bat bootRun
```

### 4. Build JAR (Optional)

```bash
./gradlew build
java -jar build/libs/daily-english-agent-0.0.1-SNAPSHOT.war
```

## Port Assignments

| Service | Port | Purpose |
|---------|------|---------|
| **Spring Boot** | 8090 | Management endpoints, Actuator health checks |
| **Ktor API** | 8091 | REST API for word-of-the-day endpoint |
| **PostgreSQL** | 5432 | Database (vocabulary_db container) |
| **Ollama** | 11434 | Local AI model server |

### Port Conflict Resolution

If ports 8090 or 8091 are in use, modify `src/main/resources/application.properties`:

```properties
server.port=8092          # Change Spring Boot port
ktor.port=8093            # Change Ktor port
```

## API Endpoints

### Get Word of the Day

```bash
curl http://localhost:8091/word-of-the-day
```

**Example Response:**

```
Hello, the word of the day is "incredible"

adjective
unbelievable | extraordinary | remarkable

English: An incredible story of triumph and tragedy
Spanish: Una historia increíble de triunfo y tragedia
```

**Response with Error (Returns Cached Word):**

```
Hello, the word of the day is "magnificent"

adjective
splendid | impressive | grand

English: The view from the mountain was magnificent
Spanish: La vista desde la montaña era magnífica

[Error: Latest fetch failed: Timeout generating vocabulary word. Showing last cached word.]
```

### Health Check - Ktor

```bash
curl http://localhost:8091/health
```

### Health Check - Spring Actuator

```bash
curl http://localhost:8090/actuator/health
```

**Example Health Response:**

```json
{
  "status": "UP",
  "components": {
    "ollamaHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "available",
        "model": "llama3.3:latest",
        "message": "Ollama model is ready"
      }
    },
    "vocabularyHealthIndicator": {
      "status": "UP",
      "details": {
        "totalWords": 15,
        "lastFetchAt": "2026-02-27 07:00:00",
        "currentWord": "incredible",
        "isActive": true,
        "hasError": false
      }
    }
  }
}
```

## Configuration

All configuration is in `src/main/resources/application.properties`:

### Database Configuration

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/englishWordsDB
spring.datasource.username=regular_user
spring.datasource.password=this_is_a_password
```

### Ollama Configuration

```properties
ollama.base.url=http://localhost:11434
ollama.model.name=llama3.3:latest
ollama.timeout.seconds=30
```

### Scheduling Configuration

```properties
# Cron expression: 0 0 7 * * * (7 AM daily)
vocabulary.fetch.cron=0 0 7 * * *
vocabulary.retry.max.attempts=5
vocabulary.retry.delay.seconds=120
```

### Port Configuration

```properties
server.port=8090
ktor.port=8091
```

## ⚠️ Important: Timezone Requirement

**The server timezone must be set to EST (Eastern Standard Time) for the 7 AM daily execution to work correctly.**

### Setting Timezone on macOS/Linux:

```bash
export TZ="America/New_York"
./gradlew bootRun
```

### Setting Timezone on Windows:

```bash
set TZ=America/New_York
gradlew.bat bootRun
```

### Verify Timezone:

```bash
date
```

## Architecture

### Component Structure

```
daily-english-agent/
├── api/ktor/                    # Ktor REST API endpoints
├── health/                      # Spring Actuator health indicators
├── model/                       # Data, Domain objects and Mappers
├── repository/                  # JPA repositories and entities
│   └── entity/
├── service/                     # Business logic
│   ├── agent/                   # Koog AI vocabulary generation
│   └── ollama/                  # Ollama model management
└── DailyEnglishAgentApplication # Main application class
```

### Data Flow

1. **Scheduled Fetch** (7 AM EST daily via Spring `@Scheduled`)
2. **Retrieve Used Words** from PostgreSQL
3. **Generate New Word** via Koog AI + Ollama llama3.3:latest
4. **Application-Level Validation** (check for duplicates, retry up to 3 times)
5. **Deactivate All Historical Words** (mark isActive = false)
6. **Save New Word** (mark isActive = true)
7. **API Access** via Ktor endpoint returns formatted response

### Error Handling Strategy

- **On Fetch Failure**: Error stored in database with timestamp
- **On API Request**: Returns last valid cached word + error message
- **On Startup (Empty DB)**: Retries 5 times with 120s delays
- **On Duplicate Generation**: Retries up to 3 times with new generation

## Behavior on Startup

### First Run (Empty Database)

1. Application checks if database is empty
2. Immediately fetches first word (doesn't wait for scheduled run)
3. Retries up to 5 times with 120-second delays if failures occur
4. Logs each attempt at INFO level

### Subsequent Runs

1. Skips initial fetch if database has words
2. Waits for next scheduled cron execution (7 AM EST daily)
3. Always returns current active word via API

## Model Auto-Pull

The application automatically pulls the Ollama model on startup:

1. Checks if `llama3.3:latest` exists locally
2. If missing, executes `ollama pull llama3.3:latest`
3. Retries up to 5 times with 120-second delays
4. Timeout: 30 seconds per pull attempt
5. Logs progress at INFO level

## Troubleshooting

### Port Already in Use

**Error:** `Address already in use: bind`

**Solution:** Change ports in `application.properties`:

```properties
server.port=9090
ktor.port=9091
```

### Database Connection Failed

**Error:** `Connection refused: postgresql://localhost:5432/englishWordsDB`

**Solution:**

1. Check if Docker container is running:
   ```bash
   docker ps | grep vocabulary_db
   ```

2. Restart container:
   ```bash
   cd docker
   docker-compose down
   docker-compose up -d
   ```

3. Verify database exists:
   ```bash
   docker exec -it vocabulary_db psql -U regular_user -d englishWordsDB
   ```

### Ollama Model Not Found

**Error:** `Ollama model llama3.3:latest is not available`

**Solution:**

1. Check Ollama is running:
   ```bash
   ollama list
   ```

2. Manually pull model:
   ```bash
   ollama pull llama3.3:latest
   ```

3. Restart application

### Timeout During Word Generation

**Error:** `Timeout generating vocabulary word`

**Solution:**

- The application will return the last cached word
- Check Ollama is responsive: `ollama run llama3.3:latest "Hello"`
- Increase timeout in `application.properties`:
  ```properties
  ollama.timeout.seconds=60
  ```

### Duplicate Words Generated

**Behavior:** Application retries 3 times if duplicate detected

**Logs:**
```
WARN: Generated word 'example' already exists. Retry attempt 1/3
```

If 3 retries fail, an error is logged and the fetch is marked as failed.

### Wrong Timezone Execution

**Issue:** Scheduled task runs at wrong time

**Solution:** Set timezone before running:

```bash
export TZ="America/New_York"
./gradlew bootRun
```

Or modify cron expression in `application.properties` to match your timezone.

## Development

### Run Tests

```bash
./gradlew test
```

### Clean Build

```bash
./gradlew clean build
```

### Check Dependencies

```bash
./gradlew dependencies
```

## Technology Stack

- **Kotlin**: 2.3.10
- **Spring Boot**: 4.1.0-SNAPSHOT
- **Ktor**: 2.3.7
- **Koog Agents**: 0.6.2 (AI framework)
- **PostgreSQL**: 16-alpine (Docker container)
- **Ollama**: llama3.3:latest model
- **Coroutines**: 1.7.3
- **JPA/Hibernate**: Database ORM
- **Spring Actuator**: Health monitoring

## License

This is an internal lab application for personal use only.

## Future Enhancements

- [ ] Add REST endpoint to manually trigger word fetch
- [ ] Add endpoint to view word history
- [ ] Support multiple languages
- [ ] Add word difficulty levels
- [ ] Email notifications for new words
- [ ] Web UI for word display
- [ ] Export vocabulary to flashcard formats

## Support

For issues or questions, check the logs at `INFO` level for detailed execution traces.

---

**Made with ☕ and 🧠 by combining Spring Boot, Ktor, and AI**
