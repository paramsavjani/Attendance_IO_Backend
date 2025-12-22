# Attendance IO Backend

RESTful API backend for the Attendance IO application. Built with Spring Boot and Kotlin, providing authentication, attendance tracking, analytics, and student management features.

## 🚀 Features

### 🔐 Authentication & Authorization
- **OAuth2 Integration** - Google OAuth2 authentication
- **Session Management** - Secure session handling with Spring Session
- **Mobile Auth Support** - Special authentication endpoints for mobile apps
- **JWT-like Sessions** - Long-lived sessions (30 days) with secure cookies

### 📊 Attendance Management
- **Mark Attendance** - Record present, absent, or cancelled classes
- **Attendance History** - Retrieve attendance records by date and subject
- **Subject Statistics** - Calculate attendance percentages per subject
- **Institute Attendance Sync** - Support for syncing with official institute records
- **Attendance Calculation** - Smart calculation considering institute attendance and manual entries

### 📈 Analytics
- **Overall Analytics** - Aggregate statistics across all semesters
- **Semester Analytics** - Detailed analytics per semester
- **Distribution Charts** - Attendance distribution across student population
- **Range Analysis** - Breakdown by attendance percentage ranges
- **Caching** - Optimized performance with Spring Cache
- **Database Views** - Pre-calculated views for fast queries

### 🔍 Search & Discovery
- **Student Search** - Search students by name or roll number
- **Attendance Lookup** - View any student's attendance history
- **Cross-semester Support** - Search across all semesters

### 📋 Subject & Timetable Management
- **Subject Management** - CRUD operations for subjects
- **Timetable Management** - Create and manage student timetables
- **Schedule Management** - Handle subject schedules and time slots
- **Semester Management** - Manage academic semesters

### 💬 Feedback System
- **Submit Feedback** - Students can submit bugs, feedback, or suggestions
- **Feedback Types** - Categorize feedback (BUG, FEEDBACK, SUGGESTION)
- **Feedback Storage** - Persistent storage in database

### 🔔 Push Notifications
- **FCM Integration** - Firebase Cloud Messaging for push notifications
- **Sleep Reminders** - Smart sleep reminder notifications based on first lecture
- **Priority Alerts** - Critical lecture notifications

### ⚙️ Configuration
- **Environment-based Config** - Separate configs for dev and production
- **Database Auto-migration** - Automatic schema updates (DDL auto-update)
- **Timezone Support** - IST (Asia/Kolkata) timezone configuration
- **Health Checks** - Actuator endpoints for monitoring

## 🛠️ Tech Stack

- **Spring Boot 4.0.0** - Application framework
- **Kotlin 2.2.21** - Programming language
- **PostgreSQL** - Relational database
- **Spring Data JPA** - Database abstraction layer
- **Spring Security** - Authentication and authorization
- **Spring OAuth2 Client** - OAuth2 integration
- **Spring Session JDBC** - Session management
- **Spring Cache** - Caching framework
- **Hibernate** - ORM framework
- **Firebase Admin SDK** - Push notifications
- **Spring Actuator** - Application monitoring

## 📦 Prerequisites

- **Java 17+** (JDK)
- **PostgreSQL 14+**
- **Gradle 8.14+** (or use Gradle wrapper)
- **Firebase Service Account** JSON file (for push notifications)

## 🚀 Installation & Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd attendance-io-backend
```

### 2. Configure Database

Create a PostgreSQL database:

```sql
CREATE DATABASE attendance_io;
```

### 3. Configure Environment Variables

Create an `application.yaml` file or set environment variables:

**Required Environment Variables:**
```yaml
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/attendance_io
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=your_google_client_id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=your_google_client_secret

FRONTEND_URL=http://localhost:8080
FIREBASE_SERVICE_ACCOUNT_PATH=path/to/firebase-service-account.json
```

### 4. Firebase Setup (Optional)

1. Download Firebase service account JSON file
2. Place it in the project root or configure the path in `application.yaml`
3. Set `FIREBASE_SERVICE_ACCOUNT_PATH` environment variable

### 5. Run the Application

**Using Gradle:**
```bash
./gradlew bootRun
```

**Using JAR:**
```bash
./gradlew bootJar
java -jar build/libs/attendance-io-backend-0.0.1-SNAPSHOT.jar
```

**Using Docker:**
```bash
docker build -t attendance-io-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=... \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  attendance-io-backend
```

## 📁 Project Structure

```
attendance-io-backend/
├── src/
│   └── main/
│       ├── kotlin/com/attendanceio/api/
│       │   ├── application/          # Business logic (App Actions)
│       │   │   ├── analytics/
│       │   │   ├── attendance/
│       │   │   ├── search/
│       │   │   ├── student/
│       │   │   ├── subject/
│       │   │   └── timetable/
│       │   ├── config/               # Configuration classes
│       │   │   ├── SecurityConfig.kt
│       │   │   ├── CorsConfig.kt
│       │   │   ├── CacheConfig.kt
│       │   │   └── AuthDebugFilter.kt
│       │   ├── controller/           # REST controllers
│       │   │   ├── analytics/
│       │   │   ├── attendance/
│       │   │   ├── authentication/
│       │   │   ├── feedback/
│       │   │   ├── search/
│       │   │   ├── semester/
│       │   │   ├── student/
│       │   │   ├── subject/
│       │   │   └── timetable/
│       │   ├── model/                # Data models
│       │   │   ├── analytics/
│       │   │   ├── attendance/
│       │   │   ├── feedback/
│       │   │   ├── schedule/
│       │   │   ├── search/
│       │   │   ├── semester/
│       │   │   ├── student/
│       │   │   ├── subject/
│       │   │   └── timetable/
│       │   ├── repository/           # Data access layer
│       │   │   ├── analytics/
│       │   │   ├── attendance/
│       │   │   ├── feedback/
│       │   │   ├── schedule/
│       │   │   ├── semester/
│       │   │   ├── student/
│       │   │   ├── subject/
│       │   │   └── timetable/
│       │   └── service/              # Services
│       │       ├── SleepReminderService.kt
│       │       ├── FcmNotificationService.kt
│       │       ├── ClassCalculationService.kt
│       │       └── ...
│       └── resources/
│           ├── application.yaml      # Development config
│           └── application-prod.yaml # Production config
├── scripts/                          # SQL scripts
├── Dockerfile                         # Docker configuration
├── build.gradle.kts                  # Gradle build file
└── run.sh                            # Docker run script
```

## 🔒 Security

- **OAuth2 Authentication** - Google OAuth2 for user authentication
- **Session-based Auth** - Secure HTTP-only cookies
- **CORS Configuration** - Configured for frontend domain
- **CSRF Protection** - Enabled for web requests
- **Secure Cookies** - HTTP-only, secure, same-site cookies in production

## 💾 Database

### Schema
The database schema is automatically managed by Hibernate with `ddl-auto: update`. Key tables include:

- `students` - Student information
- `subjects` - Subject details
- `semesters` - Semester information
- `attendance` - Attendance records
- `institute_attendance` - Official institute attendance
- `student_subject` - Student-subject relationships
- `student_timetable` - Student timetables
- `subject_schedule` - Subject schedules
- `feedback` - Feedback submissions
- `spring_session` - Session storage

### Database Views
- `student_attendance_analytics` - Pre-calculated analytics view for performance

## 🚀 Deployment

### Docker Deployment

1. **Build Docker Image:**
   ```bash
   docker build -t attendance-io-backend .
   ```

2. **Run Container:**
   ```bash
   docker run -d \
     -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e SPRING_DATASOURCE_URL=... \
     -e SPRING_DATASOURCE_USERNAME=... \
     -e SPRING_DATASOURCE_PASSWORD=... \
     attendance-io-backend
   ```

3. **Using Docker Compose** (create `docker-compose.yml`):
   ```yaml
   version: '3.8'
   services:
     backend:
       build: .
       ports:
         - "8080:8080"
       environment:
         - SPRING_PROFILES_ACTIVE=prod
         - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/attendance_io
         - SPRING_DATASOURCE_USERNAME=postgres
         - SPRING_DATASOURCE_PASSWORD=password
     db:
       image: postgres:14
       environment:
         - POSTGRES_DB=attendance_io
         - POSTGRES_USER=postgres
         - POSTGRES_PASSWORD=password
   ```

### Production Configuration

Set `SPRING_PROFILES_ACTIVE=prod` to use production configuration:
- Secure cookies enabled
- HTTPS required
- Production logging levels
- Database connection pooling

## 📊 Performance Optimizations

- **Spring Cache** - Caching for analytics endpoints
- **Database Views** - Pre-calculated views for complex queries
- **Batch Processing** - Efficient batch operations
- **Connection Pooling** - Optimized database connections
- **Indexes** - Database indexes on frequently queried columns

## 📄 License

This project is private and proprietary.

## 📞 Support

For issues, questions, or feedback, please contact the development team.

---

Made with ❤️ for Attendance IO

**Author:** Param Savjani

