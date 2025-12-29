# Buddy Service - AI Integrated Messaging Backend

Spring Boot backend service for the Buddy-UI messaging platform with AI integration.

## Tech Stack

- **Java 21** with Virtual Threads (Project Loom)
- **Spring Boot 3.4.0**
- **PostgreSQL 16+**
- **Spring AI Framework** (Google Gemini integration)
- **Maven**
- **Docker & Docker Compose**

## Prerequisites

- Java 21 JDK
- Maven 3.8+
- Docker & Docker Compose
- Google Gemini API Key

## Setup Instructions

### 1. Start PostgreSQL Database

```bash
docker-compose up -d
```

This will start:
- PostgreSQL on port `5432`
- Adminer (database admin UI) on port `8081`

Access Adminer at: http://localhost:8081
- System: PostgreSQL
- Server: postgres
- Username: buddyuser
- Password: buddypass
- Database: buddydb

### 2. Configure Google Gemini API Key

Set your Google Gemini API key as an environment variable:

```bash
export GEMINI_API_KEY=your-api-key-here
```

Or update `src/main/resources/application.yml` directly (not recommended for production).

**Note:** You can get your Gemini API key from [Google AI Studio](https://makersuite.google.com/app/apikey)

### 3. Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### POST /api/v1/chat/send

Send a message and get AI response.

**Request Body:**
```json
{
  "sessionId": "session-123",
  "content": "Hello, how are you?",
  "userId": "user-456"
}
```

**Response:**
```json
{
  "id": "uuid",
  "sessionId": "session-123",
  "senderType": "AI",
  "content": "AI response here...",
  "userId": null,
  "createdAt": "2024-01-01T12:00:00",
  "metadata": {
    "model": "gemini-1.5-flash",
    "timestamp": 1234567890
  }
}
```

## Project Structure

```
src/main/java/com/buddy/ui/
├── BuddyServiceApplication.java    # Main application class
├── config/
│   └── AiConfig.java               # AI ChatClient configuration
├── controller/
│   ├── ChatController.java         # REST API endpoints
│   └── GlobalExceptionHandler.java # Error handling
├── model/
│   ├── Message.java                # JPA entity
│   ├── SenderType.java             # Enum
│   └── dto/
│       ├── ChatRequest.java        # Request DTO
│       └── ErrorResponse.java      # Error DTO
├── repository/
│   └── MessageRepository.java      # JPA repository
└── service/
    ├── AiService.java              # AI integration service
    └── ChatService.java            # Business logic
```

## Features

- ✅ Virtual Threads enabled for high concurrency
- ✅ PostgreSQL persistence with JSONB metadata
- ✅ AI context-aware responses (last 10 messages)
- ✅ Global exception handling
- ✅ Input validation
- ✅ Docker Compose setup for local development

## Configuration

Key configuration in `application.yml`:

- Virtual Threads: `spring.threads.virtual.enabled=true`
- Database connection settings
- Google Gemini model configuration (gemini-1.5-flash by default)

## Development

### Running Tests

```bash
mvn test
```

### Database Migrations

The application uses `ddl-auto: update` for development. For production, consider using Flyway or Liquibase.

## Notes

- The AI service fetches the last 10 messages for context (configurable via `CONTEXT_WINDOW_SIZE` in `AiService`)
- All messages are persisted to PostgreSQL
- The API returns the AI's response message after processing

# buddy-be
