# Self Study Backend

A Spring Boot backend application with PostgreSQL integration for the Self Study platform.

## Features

- Spring Boot 3.2.3
- PostgreSQL database integration
- RESTful API endpoints
- Exception handling
- JPA/Hibernate for data persistence

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven
- PostgreSQL database

### Installation

1. Clone the repository
   ```bash
   git clone https://github.com/sakhamuri9/SELF-STUDY-BACKEND.git
   cd SELF-STUDY-BACKEND
   ```

2. Configure PostgreSQL
   - Create a PostgreSQL database named `selfstudy`
   - Update `src/main/resources/application.properties` with your database credentials if needed

3. Build the application
   ```bash
   mvn clean install
   ```

4. Run the application
   ```bash
   mvn spring-boot:run
   ```

## API Endpoints

- `GET /api/health` - Health check endpoint
- `GET /api/documents` - Get all documents
- `GET /api/documents/{id}` - Get document by ID
- `POST /api/documents` - Create a new document
- `PUT /api/documents/{id}` - Update an existing document
- `DELETE /api/documents/{id}` - Delete a document

## Project Structure

- `src/main/java/com/selfstudy/backend` - Main application code
  - `config` - Configuration classes
  - `controller` - REST controllers
  - `dto` - Data Transfer Objects
  - `exception` - Exception handling
  - `model` - Entity classes
  - `repository` - Data repositories
  - `service` - Business logic

## Technologies Used

- Spring Boot
- Spring Data JPA
- PostgreSQL
- Lombok
- Maven
