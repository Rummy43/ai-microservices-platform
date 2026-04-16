# AI-Driven Microservices Platform
![Build Status](https://github.com/Rummy43/ai-microservices-platform/actions/workflows/build.yml/badge.svg)

An enterprise-grade, distributed ecosystem designed for high-throughput AI processing. This platform leverages modern architectural patterns to ensure sub-millisecond latency and infinite horizontal scalability.

## 🏗 Architectural Highlights
* **High Concurrency:** Built on **Java 21 Virtual Threads** (Project Loom), allowing the system to handle millions of concurrent connections with minimal memory footprint.
* **Event-Driven Core:** Utilizing **Apache Kafka** for asynchronous, non-blocking communication between services.
* **Cloud-Native & DevOps:** Fully containerized with **Docker**, orchestrated by **Kubernetes (EKS)**, and deployed via **Terraform** on AWS.
* **Observability:** Integrated with **Spring Boot Actuator** and Prometheus/Grafana for real-time telemetry.

## 🛠 Tech Stack
- **Backend:** Java 21, Spring Boot 3.4+, MapStruct, Lombok
- **Persistence:** PostgreSQL (Production), H2 (Development)
- **Infrastructure:** AWS (EC2, RDS, EKS), API Gateway
- **Documentation:** OpenAPI 3.0 / Swagger UI