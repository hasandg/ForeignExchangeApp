# Foreign Exchange Application

A microservices-based currency conversion system built with Spring Boot, implementing CQRS pattern with MongoDB and PostgreSQL. This project started as a simple currency converter but evolved into a comprehensive system with batch processing, real-time exchange rates, and event-driven architecture.

## What This Project Does

**Key Features:**
- Real-time currency conversion with live exchange rates
- CQRS implementation with separate read/write models
- Event-driven architecture using Kafka
- RESTful APIs with comprehensive error handling
- Docker-based deployment with monitoring
- Bulk CSV processing for large-scale conversions

## Architecture Overview

The system follows a microservices architecture with clear separation of concerns:

[![Architecture Diagram](ForeignExchangeApplicationDiagram.png)] 

## Quick Start

### Prerequisites

Make sure you have these installed (learned this the hard way after spending an hour debugging):
- Docker & Docker Compose
- Java 21
- Maven 3.9+

### Running the Application

**Clone and build:**
```bash
git clone https://github.com/hasandg/foreign-exchange-app
cd foreign-exchange-app
mvn clean install
```

#### Start kafka services:
```bash
cd infrastructure/docker-compose
docker compose -f common.yml -f kafka_cluster.yml up -d
```

#### Method 1: Docker for infrastructure and microservice

##### Starting the infrastructure services and microservices with building the images of microservices:
if you don't want to build the images of microservices, you can remove the `--build` option.
```bash
cd ../..
docker compose -f infrastructure/docker-compose/common.yml -f docker-compose-all.yml up -d --build
```

Now you can access the services as described in following **API Usage** section.

##### Stopping the infrastructure services and microservices:
if you want to delete the volumes, you can add the `-v` option.
```bash
docker compose -f infrastructure/docker-compose/common.yml -f docker-compose-all.yml down
```

#### Method 2: Docker for infrastructure but microservice running locally

##### Start the infrastructure services:
```bash 
cd ../..
docker compose -f infrastructure/docker-compose/common.yml -f docker-compose.yml up -d 
```


3. **Start the applications:**
```bash
mvn clean install -DskipTests
```
##### Terminal 1 - Api Gateway
```bash
cd api-gateway
mvn spring-boot:run
```

##### Terminal 2 - Exchange Rate Service
```bash
cd exchange-rate-service
mvn spring-boot:run
```

##### Terminal 3 - Currency Conversion Service  
```bash
cd currency-conversion-service
mvn spring-boot:run
```
Now you can access the services as described in following **API Usage** section.

#### Stopping other infrastructure services:
if you want to delete the volumes, you can add the `-v` option.
```bash
cd ../..
docker compose -f infrastructure/docker-compose/common.yml -f docker-compose.yml down
```

#### Stopping kafka services:
if you want to delete the volumes, you can add the `-v` option.
```bash
cd infrastructure/docker-compose
docker compose -f common.yml -f kafka_cluster.yml down
``` 

## API Usage

### Single Currency Conversion
```bash
curl -X POST http://localhost:8081/api/conversions \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCurrency": "USD",
    "targetCurrency": "EUR", 
    "sourceAmount": 100.00
  }'
```


### Bulk CSV Processing
```bash
curl -X POST http://localhost:8083/api/conversions/bulk \
  -F "file=@sample-conversions-100.csv"
```

### Check Job Status
```bash
curl http://localhost:8083/api/batch/jobs/{jobId}/status
```

## Sample Data

I've included some CSV files for testing:
- `sample-conversions-10.csv` - Small test file (good for debugging)
- `sample-conversions-100.csv` - Medium test file (realistic load)

CSV format:
```csv
sourceCurrency,targetCurrency,sourceAmount
USD,EUR,100.50
GBP,USD,250.00
EUR,JPY,75.25
```

## Batch Processing

The bulk processing feature uses Spring Batch with some custom optimizations:

### Features
- CSV file validation and parsing
- Chunk-based processing (configurable batch size)
- Error handling and skip logic
- Progress tracking via REST API

## CQRS Implementation

The system uses CQRS (Command Query Responsibility Segregation) to separate write and read operations for performance:

### Write Model (MongoDB)
- Handles all conversion commands
- Stores operational data
- Publishes events to Kafka

### Read Model (PostgreSQL) 
- Optimized for queries and reporting
- Updated via Kafka events
- Supports complex analytics

### Event Flow
```
1. REST API receives conversion request
2. Conversion service calculates target amount
3. Save to MongoDB (Write Model)
4. Publish CONVERSION_CREATED event
5. Event consumer updates PostgreSQL (Read Model)
6. Return response to client
```

### Documentation with Swagger
The application uses Swagger for API documentation. Access it at:

#### Currency Conversion Service
http://localhost:8082/api/v1/swagger-ui/index.html
#### Exchange Rate Service
http://localhost:8083/api/v1/swagger-ui/index.html

### Monitoring

The application exposes metrics via Spring Actuator:  
http://localhost:8082/actuator/health  
http://localhost:8082/actuator/metrics  
http://localhost:8083/actuator/health  
http://localhost:8083/actuator/metrics

### Future Considerations
- **Caching**: Implement caching for frequently accessed data(For example, in conversion service, exchange rates could be cached)
- **Scaling**: Consider using Kubernetes for orchestration
- **Security**: Add authentication/authorization
- **Sharding**: For following reasons, sharding could be considerable:
     * MongoDB and TransactionId is the key for checking existence of conversion in the database
     * In our CQRS implementation, MongoDB s performance is critical for responding to the client in real-time
- **Monitoring**: Integrate with Prometheus/Grafana for better observability
- **Logging**: Use ELK stack for centralized logging