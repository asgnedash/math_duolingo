# Duolingo Math Bot

A Spring Boot Telegram bot for spaced repetition math tasks. The project includes a PostgreSQL database and uses the Telegram Bot API.

## Prerequisites

- **Java 17** or higher
- **Gradle** (or use the included `gradlew` wrapper)
- **PostgreSQL** running locally or reachable over the network

## Configuration

The application reads its configuration from environment variables or `src/main/resources/application.properties`. Set the following variables before starting the bot:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/duolingo_math_db"
export SPRING_DATASOURCE_USERNAME="<db_user>"
export SPRING_DATASOURCE_PASSWORD="<db_password>"

export TELEGRAM_BOT_USERNAME="<bot_username>"
export TELEGRAM_BOT_TOKEN="<bot_token>"
```

These correspond to the properties `spring.datasource.*` and `telegram.bot.*` in `application.properties` and can override the defaults provided in that file.

## Running the Bot

1. Ensure PostgreSQL is running and the database specified in `SPRING_DATASOURCE_URL` exists.
2. Run the application using Gradle:

```bash
./gradlew bootRun
```

   Alternatively, build a JAR and run it manually:

```bash
./gradlew build
java -jar build/libs/duolingomathbot-0.0.1-SNAPSHOT.jar
```

When the application starts, it registers the Telegram bot and you can interact with it through the usual bot commands (`/start`, `/train`, etc.).
