# Backend

Spring Boot 3 initialization for the battery recycling traceability system.

## Local Run

1. Start MySQL:

```powershell
docker compose up -d mysql
```

2. Start the backend:

```powershell
cd backend
mvn spring-boot:run
```

3. Check:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/api/v1/health`
- `http://localhost:8080/swagger-ui.html`

Flyway runs `V1__create_first_slice_schema.sql` and `V2__add_data_governance_v1_1.sql` on startup.
