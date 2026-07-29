# Fairness Checker — Codebase Map

Generated with the mapping-codebases skill (tree-sitter). Shows classes/files/line-ranges without full source — use this to navigate before reading actual files.

> Note: Java method-level signatures are not extracted by this tool (a known limitation) — classes and their line ranges are shown; open the file at the given line range for method detail.

---

## Root

# Fairness Checker/
*Files: 5 | Subdirectories: 1*

## Subdirectories

- [src/](./src/_MAP.md)

## Files

### HELP.md
- Getting Started `h1` :1

## Other Files

- fairness_tracker_dataset.json
- mvnw
- pom.xml
- temp



---

## src/

# src/
*Subdirectories: 2*

## Subdirectories

- [main/](./main/_MAP.md)
- [test/](./test/_MAP.md)



---

## src/main/

# main/
*Subdirectories: 2*

## Subdirectories

- [java/](./java/_MAP.md)
- [resources/](./resources/_MAP.md)



---

## src/main/java/

# java/
*Subdirectories: 1*

## Subdirectories

- [com/](./com/_MAP.md)



---

## src/main/java/com/

# com/
*Subdirectories: 1*

## Subdirectories

- [example/](./example/_MAP.md)



---

## src/main/java/com/example/

# example/
*Subdirectories: 1*

## Subdirectories

- [fairnesstracker/](./fairnesstracker/_MAP.md)



---

## src/main/java/com/example/fairnesstracker/

# fairnesstracker/
*Files: 1 | Subdirectories: 10*

## Subdirectories

- [config/](./config/_MAP.md)
- [controller/](./controller/_MAP.md)
- [dto/](./dto/_MAP.md)
- [entity/](./entity/_MAP.md)
- [exceptions/](./exceptions/_MAP.md)
- [repository/](./repository/_MAP.md)
- [scheduler/](./scheduler/_MAP.md)
- [security/](./security/_MAP.md)
- [seeder/](./seeder/_MAP.md)
- [service/](./service/_MAP.md)

## Files

### FairnessCheckerApplication.java
> Imports: `org.springframework.boot.SpringApplication, org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, org.springframework.web.bind.annotation.RestController`
- **FairnessCheckerApplication** (C) :8-18



---

## src/main/java/com/example/fairnesstracker/config/

# config/
*Files: 1 | Subdirectories: 1*

## Subdirectories

- [webClientConfig/](./webClientConfig/_MAP.md)

## Files

### WebClientConfig.java
> Imports: `org.springframework.beans.factory.annotation.Value, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.web.reactive.function.client.WebClient`
- **WebClientConfig** (C) :8-36



---

## src/main/java/com/example/fairnesstracker/config/webClientConfig/

# webClientConfig/
*Files: 1*

## Files

### WebClient.java
- *No top-level symbols*



---

## src/main/java/com/example/fairnesstracker/controller/

# controller/
*Files: 6*

## Files

### AlertEventController.java
> Imports: `com.example.fairnesstracker.dto.alert.AlertRequest, com.example.fairnesstracker.dto.alert.AlertResponse, com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.service.AlertService, com.example.fairnesstracker.service.EngineerService`...
- **AlertEventController** (C) :20-96

### EngineerController.java
> Imports: `com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.service.EngineerService, jakarta.validation.Valid, org.springframework.beans.factory.annotation.Autowired, org.springframework.http.ResponseEntity`...
- **EngineerController** (C) :13-52

### ImportController.java
> Imports: `com.example.fairnesstracker.dto.AlertEventImportDto, com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.repository.AlertRepository, com.example.fairnesstracker.repository.EngineerRepository, lombok.RequiredArgsConstructor`...
- **ImportController** (C) :15-84

### PagerDutyController.java
> Imports: `com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse, com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse, com.example.fairnesstracker.service.PagerDutyService, org.springframework.beans.factory.annotation.Autowired, org.springframework.http.ResponseEntity`...
- **PagerDutyController** (C) :10-59

### TestController.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.repository.AlertRepository, com.example.fairnesstracker.repository.EngineerRepository, org.springframework.beans.factory.annotation.Autowired`...
- **TestController** (C) :12-35

### WebhookController.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.repository.AlertRepository, com.example.fairnesstracker.repository.EngineerRepository, com.example.fairnesstracker.security.HmacVerifier`...
- **WebhookController** (C) :18-166



---

## src/main/java/com/example/fairnesstracker/dto/

# dto/
*Files: 1 | Subdirectories: 2*

## Subdirectories

- [alert/](./alert/_MAP.md)
- [pagerDuty/](./pagerDuty/_MAP.md)

## Files

### AlertEventImportDto.java
> Imports: `lombok.Data`
- **AlertEventImportDto** (C) :5-21



---

## src/main/java/com/example/fairnesstracker/dto/alert/

# alert/
*Files: 2*

## Files

### AlertRequest.java
> Imports: `jakarta.validation.constraints.NotNull, jakarta.validation.constraints.Pattern`
- *No top-level symbols*

### AlertResponse.java
> Imports: `java.time.LocalDateTime`
- *No top-level symbols*



---

## src/main/java/com/example/fairnesstracker/dto/pagerDuty/

# pagerDuty/
*Subdirectories: 2*

## Subdirectories

- [incident/](./incident/_MAP.md)
- [user/](./user/_MAP.md)



---

## src/main/java/com/example/fairnesstracker/dto/pagerDuty/incident/

# incident/
*Files: 7*

## Files

### Assignee.java
> Imports: `lombok.Data`
- **Assignee** (C) :6-10

### Assignment.java
> Imports: `lombok.Data`
- **Assignment** (C) :5-8

### LastStatusChangeBy.java
> Imports: `lombok.Data`
- **LastStatusChangeBy** (C) :5-9

### PagerDutyIncident.java
> Imports: `com.fasterxml.jackson.annotation.JsonProperty, lombok.Data, java.util.List`
- **PagerDutyIncident** (C) :8-35

### PagerDutyResponse.java
> Imports: `lombok.Data, java.util.List`
- **PagerDutyResponse** (C) :7-13

### Priority.java
> Imports: `lombok.Data`
- **Priority** (C) :5-8

### ServiceInfo.java
> Imports: `com.fasterxml.jackson.annotation.JsonProperty, lombok.Data`
- **ServiceInfo** (C) :6-12



---

## src/main/java/com/example/fairnesstracker/dto/pagerDuty/user/

# user/
*Files: 2*

## Files

### PagerDutyUser.java
> Imports: `lombok.Data`
- **PagerDutyUser** (C) :5-13

### PagerDutyUsersResponse.java
> Imports: `lombok.Data, java.util.List`
- **PagerDutyUsersResponse** (C) :6-16



---

## src/main/java/com/example/fairnesstracker/entity/

# entity/
*Files: 2*

## Files

### AlertEvent.java
> Imports: `jakarta.persistence, jakarta.validation.constraints.NotNull, jakarta.validation.constraints.Pattern, org.springframework.validation.annotation.Validated, java.time.LocalDateTime`
- **AlertEvent** (C) :11-58

### Engineer.java
> Imports: `com.fasterxml.jackson.annotation.JsonIgnore, jakarta.persistence, jakarta.validation.constraints.Email, jakarta.validation.constraints.NotBlank, java.util.ArrayList`...
- **Engineer** (C) :13-42



---

## src/main/java/com/example/fairnesstracker/exceptions/

# exceptions/
*Files: 4*

## Files

### ApiError.java
> Imports: `java.time.LocalDateTime`
- *No top-level symbols*

### ApiErrors.java
- **ApiErrors** (C) :3-5

### GlobalExceptionHandler.java
- *No top-level symbols*

### ResourceNotFoundException.java
- **ResourceNotFoundException** (C) :3-7



---

## src/main/java/com/example/fairnesstracker/repository/

# repository/
*Files: 2*

## Files

### AlertRepository.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.Query, org.springframework.data.repository.query.Param, org.springframework.stereotype.Repository`...
- **AlertRepository** (interface) :13-35

### EngineerRepository.java
> Imports: `com.example.fairnesstracker.entity.Engineer, org.springframework.data.jpa.repository.JpaRepository, org.springframework.stereotype.Repository, java.util.Optional`
- **EngineerRepository** (interface) :9-14



---

## src/main/java/com/example/fairnesstracker/scheduler/

# scheduler/
*Files: 1*

## Files

### PagerDutyScheduler.java
> Imports: `com.example.fairnesstracker.service.PagerDutyService, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, java.time.LocalDateTime`
- **PagerDutyScheduler** (C) :9-31



---

## src/main/java/com/example/fairnesstracker/security/

# security/
*Files: 1*

## Files

### HmacVerifier.java
> Imports: `javax.crypto.Mac, javax.crypto.spec.SecretKeySpec, java.nio.charset.StandardCharsets, java.security.MessageDigest, java.util.HexFormat`
- **HmacVerifier** (C) :9-57



---

## src/main/java/com/example/fairnesstracker/seeder/

# seeder/
*Files: 2*

## Files

### AlertDataSeeder.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.repository.AlertRepository, com.example.fairnesstracker.repository.EngineerRepository, jakarta.annotation.PostConstruct`...
- **AlertDataSeeder** (C) :15-101

### EngineerDataSeeder.java
> Imports: `com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.repository.EngineerRepository, jakarta.annotation.PostConstruct, org.springframework.beans.factory.annotation.Autowired, org.springframework.stereotype.Component`
- **EngineerDataSeeder** (C) :9-75



---

## src/main/java/com/example/fairnesstracker/service/

# service/
*Files: 4*

## Files

### AlertService.java
> Imports: `com.example.fairnesstracker.dto.alert.AlertRequest, com.example.fairnesstracker.dto.alert.AlertResponse, com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.exceptions.ResourceNotFoundException, com.example.fairnesstracker.repository.EngineerRepository`...
- **AlertService** (C) :17-109

### CsvImportService.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.repository.AlertRepository, com.example.fairnesstracker.repository.EngineerRepository, com.opencsv.CSVReader`...
- **CsvImportService** (C) :17-69

### EngineerService.java
> Imports: `com.example.fairnesstracker.entity.Engineer, com.example.fairnesstracker.exceptions.ResourceNotFoundException, com.example.fairnesstracker.repository.EngineerRepository, org.springframework.beans.factory.annotation.Autowired, org.springframework.stereotype.Service`...
- **EngineerService** (C) :12-67

### PagerDutyService.java
> Imports: `com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyIncident, com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse, com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse, com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.repository.AlertRepository`...
- **PagerDutyService** (C) :22-260



---

## src/main/resources/

# resources/
*Subdirectories: 2*

## Subdirectories

- [static/](./static/_MAP.md)
- [templates/](./templates/_MAP.md)



---

## src/test/

# test/
*Subdirectories: 3*

## Subdirectories

- [http/](./http/_MAP.md)
- [java/](./java/_MAP.md)
- [resources/](./resources/_MAP.md)



---

## src/test/java/

# java/
*Subdirectories: 1*

## Subdirectories

- [com/](./com/_MAP.md)



---

## src/test/java/com/

# com/
*Subdirectories: 1*

## Subdirectories

- [example/](./example/_MAP.md)



---

## src/test/java/com/example/

# example/
*Subdirectories: 1*

## Subdirectories

- [fairnesstracker/](./fairnesstracker/_MAP.md)



---

## src/test/java/com/example/fairnesstracker/

# fairnesstracker/
*Files: 1 | Subdirectories: 2*

## Subdirectories

- [controller/](./controller/_MAP.md)
- [repository/](./repository/_MAP.md)

## Files

### FairnessCheckerApplicationTests.java
> Imports: `org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, org.springframework.test.context.ActiveProfiles`
- *No top-level symbols*



---

## src/test/java/com/example/fairnesstracker/controller/

# controller/
*Files: 2*

## Files

### AlertControllerTest.java
> Imports: `com.example.fairnesstracker.service.AlertService, com.example.fairnesstracker.service.EngineerService, org.junit.jupiter.api.Test, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`...
- *No top-level symbols*

### EngineerControllerTest.java
> Imports: `com.example.fairnesstracker.service.EngineerService, org.junit.jupiter.api.Test, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.http.MediaType`...
- **EngineerControllerTest** (C) :16-41



---

## src/test/java/com/example/fairnesstracker/repository/

# repository/
*Files: 2*

## Files

### AlertRepositoryTest.java
> Imports: `com.example.fairnesstracker.entity.AlertEvent, com.example.fairnesstracker.entity.Engineer, org.junit.jupiter.api.Test, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest`...
- *No top-level symbols*

### EngineerRepositiryTest.java
> Imports: `com.example.fairnesstracker.entity.Engineer, org.junit.jupiter.api.Test, org.springframework.beans.factory.annotation.Autowired, org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest, org.springframework.test.context.ActiveProfiles`...
- *No top-level symbols*



---
