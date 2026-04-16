# User Service

The core identity and profile management module of the AI Microservices Platform.

## 🚀 Key Features
- **Stateless Architecture:** Uses **UUID v4/v7** for decentralized ID generation, enabling multi-region database synchronization.
- **Automated Auditing:** Implements **JPA Auditing** to track data lineage (`createdAt`, `modifiedBy`).
- **High-Performance Mapping:** Utilizes **MapStruct** for compile-time DTO-to-Entity transformation, eliminating reflection overhead.
- **Robust Exception Handling:** Centralized **Global Exception Handler** providing RFC-7807 compliant error responses.

## 🛠 Local Setup
1. **Prerequisites:** JDK 21+, Gradle 8.x
2. **Build:** `./gradlew clean build`
3. **Run:** `./gradlew bootRun`
4. **API Docs:** Access `http://localhost:8080/swagger-ui.html` once the service is started.

## 📈 Performance Optimization
This service is tuned for **Java 21 Virtual Threads**. By setting `spring.threads.virtual.enabled=true`, the service utilizes lightweight threads, significantly reducing the overhead of traditional thread-per-request models.