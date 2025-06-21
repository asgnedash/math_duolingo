# Math Duolingo Bot

This project is a Telegram bot built with Spring Boot. It requires certain environment variables for configuration.

## Required Environment Variables

- `DB_USERNAME` – PostgreSQL username used by Spring Data JPA
- `DB_PASSWORD` – PostgreSQL user password
- `TELEGRAM_BOT_TOKEN` – token provided by [@BotFather](https://t.me/BotFather)

Set these variables in your environment before running the application so that Spring Boot can resolve the placeholders in `application.properties`.
