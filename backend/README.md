# CampusFind — Spring Boot Backend (Phase 5: MySQL Integration & JPA Entities)

This directory contains the Spring Boot backend service for the **CampusFind** college project.

## Environment & Prerequisites
- **Java:** OpenJDK 25 (LTS) / 21+
- **Build Tool:** Apache Maven 3.10.0+
- **Spring Boot Version:** 3.4.2
- **RDBMS:** MySQL Community Server 8.0.44

## Architecture Overview (Phase 5)
In Phase 5, the backend has been augmented with Spring Data JPA and MySQL integration:
- **JPA Entities:** Located in `com.campusfind.entity` (`User`, `Report`, `Claim`, `RecoveryRecord`).
- **Enums:** Located in `com.campusfind.entity.enums` (`ReportType`, `ReportStatus`, `ClaimStatus`, `UserRole`, `AccountStatus`).
- **Repositories:** Located in `com.campusfind.repository` (`UserRepository`, `ReportRepository`, `ClaimRepository`, `RecoveryRecordRepository`).
- **Port:** Configured to run on `http://localhost:8080`.
- **Health Verification:** `GET /api/health` returns `{"status":"UP","application":"CampusFind Backend"}`.
- **Database Schema Validation:** `spring.jpa.hibernate.ddl-auto=validate` strictly validates that MySQL tables match entity definitions without executing any schema mutations.

## Project Structure
```
backend/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/campusfind/
    │   │   ├── CampusFindApplication.java
    │   │   ├── controller/
    │   │   │   └── HealthController.java
    │   │   ├── dto/
    │   │   │   └── HealthResponse.java
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Report.java
    │   │   │   ├── Claim.java
    │   │   │   ├── RecoveryRecord.java
    │   │   │   └── enums/
    │   │   │       ├── ReportType.java
    │   │   │       ├── ReportStatus.java
    │   │   │       ├── ClaimStatus.java
    │   │   │       ├── UserRole.java
    │   │   │       └── AccountStatus.java
    │   │   └── repository/
    │   │       ├── UserRepository.java
    │   │       ├── ReportRepository.java
    │   │       ├── ClaimRepository.java
    │   │       └── RecoveryRecordRepository.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/campusfind/
            ├── CampusFindApplicationTests.java
            ├── controller/
            │   └── HealthControllerTest.java
            └── entity/
                └── EntityMetadataTest.java
```

## Running the Application on Windows

### 1. Build and Run Tests
Run the Maven test suite:
```powershell
& "C:\Users\Lenovo\OneDrive\Desktop\Tools\apache-maven-3.10.0\bin\mvn.cmd" test
```

### 2. Package the Application
Build the executable JAR:
```powershell
& "C:\Users\Lenovo\OneDrive\Desktop\Tools\apache-maven-3.10.0\bin\mvn.cmd" package
```

### 3. Running with MySQL Credentials
Set your MySQL password in the PowerShell session before launching:
```powershell
# Set database password for the current session (replace with your actual password)
$env:DB_PASSWORD = "your_mysql_password"

# Optional: set custom username if not 'root'
$env:DB_USERNAME = "root"

# Run with Maven
& "C:\Users\Lenovo\OneDrive\Desktop\Tools\apache-maven-3.10.0\bin\mvn.cmd" spring-boot:run
```

Or run the packaged JAR directly:
```powershell
$env:DB_PASSWORD = "your_mysql_password"
java -jar target/campusfind-backend-0.0.1-SNAPSHOT.jar
```

## Testing the Health Endpoint
Once started, test the health endpoint in PowerShell:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
```

Expected JSON response:
```json
{
  "status": "UP",
  "application": "CampusFind Backend"
}
```
