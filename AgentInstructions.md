# Role & Objective
You are a Staff Software Engineer specializing in high-throughput, low-latency Java Spring Boot architectures. 
Your objective is to implement an LLM Shadow Proxy using Java 21, Spring Boot 3.x, and Gradle. 

This proxy must intercept incoming customer traffic, serve them synchronously using a Primary LLM mock endpoint, and safely fire an asynchronous shadow request to a Candidate LLM mock endpoint. Mismatched outputs must be logged, and real-time metrics tracked.

# Core System Design Requirements

## 1. Latency & Thread Decoupling (Crucial)
* Use Java 21 Virtual Threads (`@Async` backed by a `VirtualThreadTaskExecutor` or a dedicated `ExecutorService` configured for virtual threads).
* The Primary LLM request path must be completely non-blocking. 
* The background Candidate LLM request context MUST survive even if the client closes the HTTP connection.
* High latency or failures from the Candidate LLM must have ZERO impact on the Primary LLM response time or success rate.

## 2. Data Consistency & Normalization Pipeline
* Implement a robust parsing/normalization utility for comparing LLM outputs.
* Before comparing text, the normalization logic must:
  - Strip markdown JSON blocks (e.g., remove ```json and ``` syntax).
  - Parse valid JSON strings and serialize them deterministically (sorted keys, no spaces).
  - Collapse all duplicate whitespaces/newlines into a single space.
  - Lowercase all raw text strings and strip trailing punctuation.

## 3. Real-Time Thread-Safe Metrics
* Build a thread-safe metrics tracker backed by a pluggable `CounterStore`:
  - `InMemoryCounterStore` — lock-free `AtomicLong` per JVM (default for single instance).
  - `RedisCounterStore` — cluster-wide totals when multiple instances sit behind a load balancer (`metrics.store=redis`).
* Do not use synchronized blocks. Ensure low-overhead reads/writes.
* Track: `total_shadow_requests`, `matches`, `mismatches`, `candidate_failures`, `shadow_skipped`, `shadow_dropped`, and `real_time_match_rate` (calculated dynamically as a percentage).
* Expose snapshots via `GET /metrics` including `instance_id` (container hostname) and `scope` (`"instance"` or `"cluster"`).
* Apply probabilistic shadow sampling via `llm.shadow.sample-rate` (100% locally, 10% in `prod` profile).
* Metrics update after async shadow work completes, not when `/generate` returns.

# Technical Implementation Details

## Dependencies & Setup (build.gradle)
* Use Gradle (Kotlin DSL `build.gradle.kts` or Groovy `build.gradle`).
* Include dependencies: `spring-boot-starter-web`, `spring-boot-starter-actuator` (optional, or write a custom controller), `lombok`, and `spring-boot-starter-test`.
* Use Spring `RestClient` or `WebClient` for external API calls. Ensure timeout configurations are explicitly set (e.g., 500ms connection timeout for Primary, 2000ms for Candidate).

## Code Structure Guidelines
Please generate or modify the project to match this layout:
1. `config/AsyncConfig.java`: Configure Virtual Threads (`java.lang.Thread.ofVirtual()`) to handle background processing seamlessly.
2. `model/PromptRequest.java` & `model/LLMResponse.java`: Standard DTO records or classes using Lombok.
3. `service/LLMMockService.java`: Simulates Primary LLM (100ms artificial delay) and Candidate LLM (500ms artificial delay).
4. `service/MetricsTracker.java`: Counter storage via `CounterStore`; exposes `MetricsSnapshot` for `/metrics`.
5. `metrics/CounterStore.java`, `InMemoryCounterStore.java`, `RedisCounterStore.java`: Pluggable counter backends.
6. `support/InstanceIdentity.java`: Resolves container hostname for metrics attribution.
7. `service/ShadowProcessor.java`: The asynchronous component with sampling, concurrency limits, and `normalizeAndCompare()`.
8. `controller/ProxyController.java`: Exposes `@PostMapping("/generate")` and `@GetMapping("/metrics")`.

# Testing Strategy
* Generate a Spring Boot Slice Test (`@WebMvcTest`) or Integration Test (`@SpringBootTest`) using `TestRestTemplate` / `WebTestClient`.
* Write a specific integration test that measures elapsed execution time: verify that the `/generate` endpoint responds in <150ms even when the Candidate LLM takes 500ms+ to process.
* Write unit tests covering the string normalization utility to ensure formatting discrepancies do not trigger false mismatches.

# CI/CD Pipeline
* Provide a `.github/workflows/ci.yml` file executing standard `./gradlew build` steps on Java 21 to validate the code on push.

Please review the current workspace structure, and generate the necessary Java packages and code blocks cleanly following these strict instructions. Start by analyzing or creating the `build.gradle` file first.
