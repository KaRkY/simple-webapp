---
title: Refactor email-monitor.* to @ConfigurationProperties
created: 2026-03-20T02:14:47Z
context: email-monitor-config-properties
description: Replace scattered @Value injections for email-monitor.* properties with a single @ConfigurationProperties record.
---

assumptions:
- No existing @ConfigurationProperties usage in the project.
- spring-boot-configuration-processor not yet in pom.xml.
- @Scheduled cron annotations must keep ${...} placeholder syntax (Spring limitation — cannot read from a bean at annotation processing time).
- All 4 properties go into one record: cron, batchSize, staleTolerance, monitorCron.
- Defaults encoded via @DefaultValue to avoid repeating them in application.yaml.

phases:
- phase: 1 — Add spring-boot-configuration-processor
  file: pom.xml
  change: Add optional dependency in <dependencies>:
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>

- phase: 2 — Create EmailMonitorProperties record
  file: src/main/java/simple/simple_webapp/email/internal/EmailMonitorProperties.java
  content: |
    @ConfigurationProperties(prefix = "email-monitor")
    record EmailMonitorProperties(
        @DefaultValue("0/2 * * * * *") String cron,
        @DefaultValue("10") int batchSize,
        @DefaultValue("PT5M") Duration staleTolerance,
        @DefaultValue("0 * * * * *") String monitorCron) {}
  also:
  file: src/main/java/simple/simple_webapp/email/internal/EmailConfig.java
  change: Add @EnableConfigurationProperties(EmailMonitorProperties.class)

- phase: 3 — Refactor EmailSenderJob
  file: src/main/java/simple/simple_webapp/email/internal/EmailSenderJob.java
  changes:
    - Remove @Value batchSize constructor param.
    - Inject EmailMonitorProperties properties.
    - Replace this.batchSize = batchSize with this.batchSize = properties.batchSize().
    - @Scheduled(cron = "${email-monitor.cron:0/2 * * * * *}") stays unchanged.

- phase: 4 — Refactor EmailMonitorJob
  file: src/main/java/simple/simple_webapp/email/internal/EmailMonitorJob.java
  changes:
    - Remove @Value staleTolerance constructor param.
    - Inject EmailMonitorProperties properties.
    - Replace this.staleTolerance = staleTolerance with this.staleTolerance = properties.staleTolerance().
    - @Scheduled(cron = "${email-monitor.monitor-cron:0 * * * * *}") stays unchanged.

- phase: 5 — Verify
  command: ./mvnw test -Dtest=EmailManagementTests

open_questions: []

status: ready
