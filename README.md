# Dispatch Guide Management System with RabbitMQ

A distributed dispatch guide management system built with **Spring Boot**, following a microservices architecture and integrating **RabbitMQ**, **Oracle Autonomous Database**, **Amazon S3**, **Docker**, **Docker Compose**, and **GitHub Actions**.

## Architecture

```
                +----------------------+
                |      API Gateway     |
                +----------+-----------+
                           |
             +-------------+-------------+
             |                           |
             v                           v
      Producer Service             Consumer Service
      (Port 8080)                 (Port 8081)
             |                           |
             | RabbitMQ                  |
             +---------> Queue ----------+
                           |
                           v
                     Process message
                           |
                 Upload guide to Amazon S3
                           |
                 Save processing summary
                    in Oracle Database
```

---

## Technologies

- Java 17
- Spring Boot 3
- Spring Data JPA
- Spring Security (OAuth2 JWT)
- Oracle Autonomous Database
- RabbitMQ
- Amazon S3
- Docker
- Docker Compose
- GitHub Actions
- Oracle Wallet

---

## Features

### Producer Service

- Create dispatch guides
- Generate guide files (.txt)
- Store guide files in shared storage (EFS)
- Publish guide metadata to RabbitMQ
- CRUD operations for dispatch guides
- Download guides
- History endpoint

### Consumer Service

- Consume messages from RabbitMQ
- Read generated guide from shared storage
- Upload guide to Amazon S3
- Save processing summary in Oracle Database
- Dead Letter Queue (DLQ) support
- Manual message consumption endpoint

---

## RabbitMQ Flow

```
POST /api/guias
        │
        ▼
Producer
        │
        ▼
dispatch-guide-exchange
        │
        ▼
dispatch-guide-queue
        │
        ▼
Consumer
        │
        ├── Upload file to S3
        ├── Save summary in Oracle
        └── ACK
```

If an error occurs while processing:

```
dispatch-guide-queue
        │
        ▼
NACK
        │
        ▼
dispatch-guide-dlq
```

---

## Project Structure

```
GuideFilesVer2
│
├── bdget-producer
│   ├── src
│   ├── Dockerfile
│   └── pom.xml
│
├── bdget-consumer
│   ├── src
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml
├── docker-compose.prod.yml
└── .github/workflows
```

---

## Local Execution

```bash
docker compose up --build
```

Services:

| Service | Port |
|----------|------|
| Producer | 8080 |
| Consumer | 8081 |
| RabbitMQ | 5672 |
| RabbitMQ Management | 15672 |

---

## Deployment

Deployment is fully automated using **GitHub Actions**.

Each push to the `main` branch:

- Builds both microservices
- Creates Docker images
- Pushes images to Docker Hub
- Generates the production `.env`
- Copies the production Docker Compose file to the EC2 instance
- Pulls the latest images
- Deploys using Docker Compose

---

## Environment Variables

Environment variables are managed using **GitHub Secrets** and are **not stored in the repository**.

Oracle Wallet is mounted as a Docker volume and is never committed to GitHub.

---

## Security

Sensitive information is excluded from the repository:

- Oracle Wallet
- AWS Credentials
- Oracle Database credentials
- Docker deployment environment
- Local `.env` files

---

## Authors

Sofía Medina & Sebastián Tapia.
Developed as part of a Cloud Computing course project using Spring Boot microservices and RabbitMQ.
