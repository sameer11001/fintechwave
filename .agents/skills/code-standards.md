# Java & Spring Microservice — Code Style & Staff-Level Execution Standards

> This document is the authoritative coding standard for all Java/Spring microservice work.
> Every AI agent, developer, and reviewer must treat these rules as non-negotiable defaults.

---

## Table of Contents

1. [Project Structure](#1-project-structure)
2. [Naming Conventions](#2-naming-conventions)
3. [Package Organization](#3-package-organization)
4. [Layer Architecture](#4-layer-architecture)
5. [REST Controller Standards](#5-rest-controller-standards)
6. [Service Layer Standards](#6-service-layer-standards)
7. [Repository Layer Standards](#7-repository-layer-standards)
8. [Entity & Domain Model Standards](#8-entity--domain-model-standards)
9. [DTO & Request/Response Standards](#9-dto--requestresponse-standards)
10. [Exception Handling](#10-exception-handling)
11. [API Response Envelope](#11-api-response-envelope)
12. [Validation Standards](#12-validation-standards)
13. [Security Standards](#13-security-standards)
14. [Configuration & Properties](#14-configuration--properties)
15. [Logging Standards](#15-logging-standards)
16. [Testing Standards](#17-testing-standards)
17. [Dependency Injection Rules](#18-dependency-injection-rules)
18. [Transaction Management](#19-transaction-management)
19. [Inter-Service Communication](#20-inter-service-communication)
20. [General Code Hygiene](#21-general-code-hygiene)

---

## 1. Project Structure

Every microservice must follow this exact Maven/Gradle module layout:

```
my-service/
├── src/
│   ├── main/
│   │   ├── java/com/company/myservice/
│   │   │   ├── MyServiceApplication.java
│   │   │   ├── config/
│   │   │   ├── api/       ← This is like a controller layer
│   │   │   ├── service/
│   │   │   │   └── impl/
│   │   │   ├── repository/
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   └── enums/
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── exception/
│   │   │   ├── mapper/
│   │   │   ├── security/
│   │   │   └── util/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/         ← Flyway scripts
└── src/
    └── test/
        └── java/com/company/myservice/
            ├── controller/
            ├── service/
            └── repository/
```

**Rules:**

- One service = one bounded context. Never mix two domain concerns in one service.
- `Application.java` lives at the root package only — never in a sub-package.
- `config/` holds only Spring `@Configuration` classes — no business logic ever.
- `util/` holds only pure static helper methods with zero Spring dependencies.

---

## 2. Naming Conventions

### Classes

| Type                   | Convention                  | Example                                                |
| ---------------------- | --------------------------- | ------------------------------------------------------ |
| Entity                 | `PascalCase` + no suffix    | `User`, `Order`, `TenantConfig`                        |
| Repository             | Entity name + `Repository`  | `UserRepository`                                       |
| Service Interface      | Entity/domain + `Service`   | `UserService`                                          |
| Service Implementation | Interface name + `Impl`     | `UserServiceImpl`                                      |
| Controller             | Domain + `Controller`       | `UserController`                                       |
| DTO (Request)          | Action + Entity + `Request` | `CreateUserRequest`, `UpdateOrderRequest`              |
| DTO (Response)         | Entity + `Response`         | `UserResponse`, `OrderSummaryResponse`                 |
| Mapper                 | Entity + `Mapper`           | `UserMapper`                                           |
| Exception              | Descriptive + `Exception`   | `UserNotFoundException`, `TenantAccessDeniedException` |
| Enum                   | `PascalCase`, singular      | `UserStatus`, `OrderType`                              |
| Config class           | Purpose + `Config`          | `SecurityConfig`, `RedisConfig`                        |
| Constants              | `PascalCase` + `Constants`  | `AppConstants`, `SecurityConstants`                    |

### Methods

| Convention                                    | Rule                                          |
| --------------------------------------------- | --------------------------------------------- | ---------------------------------- |
| `camelCase` always                            | `getUserById`, `createOrder`                  |
| Boolean methods                               | Start with `is`, `has`, `can`                 | `isActive()`, `hasPermission()`    |
| Repository finders                            | Start with `find`, never `get`                | `findByEmail`, `findAllByTenantId` |
| Void side-effect methods                      | Use verb: `save`, `delete`, `send`, `publish` |                                    |
| Avoid ambiguous `process`, `handle`, `manage` | Use specific verbs instead                    |                                    |

### Variables & Fields

```java
// CORRECT
private final UserRepository userRepository;
private static final int MAX_RETRY_COUNT = 3;
private static final String DEFAULT_ROLE = "ROLE_USER";

// WRONG — no abbreviations, no Hungarian notation
private final UserRepository ur;
private int maxRetryCnt;
private String strDefaultRole;
```

### Constants

```java
// All constants in a dedicated Constants class — never scattered as public static final in entities
public final class AppConstants {
    private AppConstants() {}

    public static final String BEARER_PREFIX = "Bearer ";
    public static final int TOKEN_EXPIRY_HOURS = 24;
    public static final String DEFAULT_TENANT_HEADER = "X-Tenant-ID";
}
```

---

## 3. Package Organization

**Rule: organize by feature/domain, not by layer type at the root level for large services.**

---

## 4. Layer Architecture

Strict unidirectional dependency flow. No layer may import from a layer above it.

```
Controller  →  Service Interface  →  Repository
                     ↓
                  Domain (Entity, Enum)
                     ↓
                  DTO (Request/Response)
```

**Hard rules:**

- Controllers never call repositories directly — ever.
- Entities never appear in controller method signatures — ever.
- Service implementations never import other service implementations directly; depend on interfaces.
- Mappers are the only classes allowed to touch both entity and DTO.

---

## 5. REST Controller Standards

### Class-level setup

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    private final UserService userService;
}
```

**Rules:**

- Always version the base path: `/api/v1/`, `/api/v2/`
- `@RequiredArgsConstructor` from Lombok — never `@Autowired` on fields
- Always annotate with `@Tag` for OpenAPI documentation
- Never put business logic in a controller — only orchestration + mapping

### Method standards

```java
@GetMapping("/{id}")
@Operation(summary = "Get user by ID")
public ResponseEntity<ApiResponse<UserResponse>> getUserById(
        @PathVariable @Positive Long id) {

    UserResponse response = userService.findById(id);
    return ResponseEntity.ok(ApiResponse.success(response));
}

@PostMapping
@Operation(summary = "Create a new user")
public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @RequestBody @Valid CreateUserRequest request) {

    UserResponse response = userService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
}

@PutMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> updateUser(
        @PathVariable @Positive Long id,
        @RequestBody @Valid UpdateUserRequest request) {

    UserResponse response = userService.update(id, request);
    return ResponseEntity.ok(ApiResponse.success(response));
}

@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Void>> deleteUser(
        @PathVariable @Positive Long id) {

    userService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null));
}

@GetMapping
public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String direction) {

    Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.fromString(direction), sortBy));
    Page<UserResponse> users = userService.findAll(pageable);
    return ResponseEntity.ok(ApiResponse.success(users));
}
```

**HTTP Status Rules:**
| Operation | Status |
|---|---|
| Successful GET | `200 OK` |
| Successful POST (create) | `201 CREATED` |
| Successful PUT/PATCH | `200 OK` |
| Successful DELETE | `200 OK` or `204 NO CONTENT` (pick one, be consistent) |
| Validation failure | `400 BAD REQUEST` |
| Authentication failure | `401 UNAUTHORIZED` |
| Authorization failure | `403 FORBIDDEN` |
| Resource not found | `404 NOT FOUND` |
| Conflict (duplicate) | `409 CONFLICT` |
| Server error | `500 INTERNAL SERVER ERROR` |

---

## 6. Service Layer Standards

### Interface definition

```java
public interface UserService {
    UserResponse findById(Long id);
    UserResponse create(CreateUserRequest request);
    UserResponse update(Long id, UpdateUserRequest request);
    void delete(Long id);
    Page<UserResponse> findAll(Pageable pageable);
    Page<UserResponse> findAllByTenant(Long tenantId, Pageable pageable);
}
```

### Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)   // default read-only; override per method
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);
        log.info("User created successfully with id={}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        userMapper.updateEntityFromRequest(request, user);
        User updatedUser = userRepository.save(user);

        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
        log.info("User deleted with id={}", id);
    }
}
```

**Rules:**

- `@Transactional(readOnly = true)` at class level, override with `@Transactional` on write methods
- Always depend on the interface, not the implementation
- Never return `null` — throw a domain exception or return `Optional` explicitly
- Service methods must be cohesive — one public method = one business action
- No `System.out.println` — always `@Slf4j` + `log.info/warn/error`

---

## 7. Repository Layer Standards

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    Page<User> findAllByTenantId(Long tenantId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.status = :status")
    List<User> findActiveUsersByTenant(
            @Param("tenantId") Long tenantId,
            @Param("status") UserStatus status);

    // Use @Modifying for bulk updates — never fetch then update in a loop
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.tenantId = :tenantId")
    int updateStatusByTenant(@Param("tenantId") Long tenantId,
                             @Param("status") UserStatus status);
}
```

**Rules:**

- Always use `Optional<T>` for single-entity finders — never return `null`
- Use `@Query` for anything beyond simple property-based finders
- Use `@Modifying` + `@Query` for bulk updates — never load and iterate
- Prefer named parameters `@Param` over positional in JPQL
- Do not add custom logic inside repository implementations — use the service layer
- For complex queries, use `JpaSpecificationExecutor<T>` or QueryDSL

---

## 8. Entity & Domain Model Standards

```java
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private Long version;   // optimistic locking — always include

    // Relationships — always lazy by default
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserSession> sessions = new ArrayList<>();
}
```

**Rules:**

- Always `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- Always include `@Version` for optimistic locking on mutable entities
- Always use `@CreatedDate`, `@LastModifiedDate` from Spring Data Auditing
- All relationships are `LAZY` by default — use `JOIN FETCH` in JPQL when needed
- Never use `@Data` from Lombok on entities — it generates `equals/hashCode` based on all fields which breaks Hibernate proxies. Use `@Getter @Setter` separately
- Always define `@Table` with explicit `name` and relevant `indexes`
- Column names in `snake_case` always
- Never put business logic inside entities (no anemic/rich domain debate — keep entities as pure data holders in Spring projects)

### Enums

```java
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
```

---

## 9. DTO & Request/Response Standards

### Request DTOs

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).*$",
             message = "Password must contain at least one uppercase letter and one number")
    private String password;

    @NotNull(message = "Role ID is required")
    @Positive(message = "Role ID must be positive")
    private Long roleId;
}
```

### Response DTOs

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // never serialize null fields
public class UserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private String roleName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
```

**Rules:**

- Request DTOs use `@Data` (Lombok) — they are mutable by design
- Response DTOs use `@Data` + `@JsonInclude(NON_NULL)` — never expose null fields to consumers
- Never expose entity fields like `password`, `version`, `tenantId` in responses
- Always use `@JsonFormat` for date/time fields
- All timestamps in responses use ISO-8601 format
- Separate request DTOs per operation: `CreateUserRequest`, `UpdateUserRequest` — never reuse the same DTO for both

---

## 10. Exception Handling

### Custom exception hierarchy

```java
// Base exception
public abstract class BaseServiceException extends RuntimeException {
    private final String errorCode;

    protected BaseServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

// 404 base
public abstract class ResourceNotFoundException extends BaseServiceException {
    protected ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}

// Concrete exceptions
public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }

    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }
}

public class DuplicateResourceException extends BaseServiceException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super(String.format("%s already exists with %s: %s", resource, field, value),
              "DUPLICATE_RESOURCE");
    }
}

public class TenantAccessDeniedException extends BaseServiceException {
    public TenantAccessDeniedException() {
        super("Access denied for this tenant", "TENANT_ACCESS_DENIED");
    }
}
```

### Global exception handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "Invalid value")
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

**Rules:**

- Never catch `Exception` in service/controller methods — let it bubble to `GlobalExceptionHandler`
- Always log at `WARN` level for business exceptions, `ERROR` for unexpected ones
- Never expose stack traces or internal error details in API responses
- Every custom exception must carry a machine-readable `errorCode` string

---

## 11. API Response Envelope

All API responses must use this consistent envelope:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String errorCode;
    private String message;
    private T data;
    private Map<String, String> validationErrors;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Factory methods
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> validationErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed")
                .validationErrors(validationErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
```

**Successful response:**

```json
{
  "success": true,
  "data": { "id": 1, "email": "user@example.com" },
  "timestamp": "2024-01-15T10:30:00"
}
```

**Error response:**

```json
{
  "success": false,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "User not found with id: 99",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Validation error response:**

```json
{
  "success": false,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "validationErrors": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## 12. Validation Standards

```java
// Always validate at controller entry — never in service
@PostMapping
public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @RequestBody @Valid CreateUserRequest request) { ... }

// For path variables and query params — use @Validated at class level
@RestController
@Validated
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable @Positive(message = "ID must be a positive number") Long id) { ... }
}

// Custom validator example
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface ValidPhoneNumber {
    String message() default "Invalid phone number format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{7,14}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // use @NotNull for null checks separately
        return PHONE_PATTERN.matcher(value).matches();
    }
}
```

**Rules:**

- Never validate manually in service with `if (field == null)` — use Bean Validation annotations
- Keep validation annotations on DTOs, not entities
- For cross-field validation (e.g. password confirmation), use a class-level `@Constraint`
- Custom validation messages must be user-friendly, not technical

---

## 13. Security Standards

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

// JWT Filter
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
```

**Rules:**

- Always stateless JWT — never server-side sessions in microservices
- Never log JWT tokens or passwords at any log level
- Use `@PreAuthorize("hasRole('ADMIN')")` for method-level security
- Secrets (JWT secret, DB passwords) must come from environment variables or Vault — never in `.yml` committed to VCS
- Multi-tenant: always validate `tenantId` in service layer before any data access

---

## 14. Configuration & Properties

### application.yml structure

```yaml
spring:
  application:
    name: user-service

  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000

  jpa:
    hibernate:
      ddl-auto: validate # always validate in prod — never create/update
    show-sql: false # never true in prod
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

server:
  port: 8080
  servlet:
    context-path: /

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-hours: ${JWT_EXPIRATION_HOURS:24}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
```

### Typed configuration properties

```java
// Always bind external config to a typed @ConfigurationProperties class
@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Data
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(1)
    @Max(168)
    private int expirationHours = 24;
}

// Register it
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class MyServiceApplication { ... }
```

**Rules:**

- All sensitive values via environment variables `${ENV_VAR}` — never hardcoded
- Always use `@ConfigurationProperties` for multi-field config groups — never `@Value` scattered everywhere
- `ddl-auto: validate` in all non-local environments
- Database migrations via Flyway only — naming: `V{version}__{description}.sql`

---

## 15. Logging Standards

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        log.debug("Creating user with email={}", request.getEmail());

        User saved = userRepository.save(user);

        log.info("User created: id={}, tenantId={}", saved.getId(), saved.getTenantId());
        return userMapper.toResponse(saved);
    }
}
```

### Log level guidelines

| Level   | When to use                                                                  |
| ------- | ---------------------------------------------------------------------------- |
| `TRACE` | Extremely granular — loop iterations, raw payloads. Disabled in prod.        |
| `DEBUG` | Method entry, intermediate values. Disabled in prod.                         |
| `INFO`  | State changes, business events: user created, order placed, email sent.      |
| `WARN`  | Expected but notable: resource not found, auth failure, retry attempt.       |
| `ERROR` | Unexpected failures that need human attention. Always include the exception. |

```java
// CORRECT — structured parameters, not string concatenation
log.info("Order processed: orderId={}, userId={}, amount={}", orderId, userId, amount);
log.error("Failed to send email to userId={}", userId, exception);   // exception as last arg

// WRONG
log.info("Order processed: " + orderId);    // string concat allocates memory even when disabled
log.error(exception.getMessage());          // loses stack trace
```

**Rules:**

- Never log passwords, tokens, PII (email, phone, national ID) at any level
- Always pass the exception object as the last argument to `log.error()` — never `ex.getMessage()`
- Use MDC for request tracing: set `requestId`, `tenantId`, `userId` at filter level

```java
// In a request filter:
MDC.put("requestId", UUID.randomUUID().toString());
MDC.put("tenantId", String.valueOf(tenantId));
try {
    filterChain.doFilter(request, response);
} finally {
    MDC.clear();
}
```

---

## 16. Testing Standards

### Unit tests — Service layer

```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("findById — should return user response when user exists")
    void findById_WhenUserExists_ReturnsResponse() {
        // Arrange
        Long userId = 1L;
        User user = buildUser(userId);
        UserResponse expected = buildUserResponse(userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userMapper.toResponse(user)).willReturn(expected);

        // Act
        UserResponse result = userService.findById(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        then(userRepository).should(times(1)).findById(userId);
    }

    @Test
    @DisplayName("findById — should throw UserNotFoundException when user does not exist")
    void findById_WhenUserNotFound_ThrowsException() {
        given(userRepository.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
```

### Integration tests — Controller layer

```java
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/users — should return 201 with created user")
    @WithMockUser(roles = "ADMIN")
    void createUser_ValidRequest_Returns201() throws Exception {
        CreateUserRequest request = buildCreateUserRequest();
        UserResponse response = buildUserResponse(1L);

        given(userService.create(any(CreateUserRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(response.getEmail()));
    }

    @Test
    @DisplayName("POST /api/v1/users — should return 400 when email is invalid")
    @WithMockUser
    void createUser_InvalidEmail_Returns400() throws Exception {
        CreateUserRequest request = buildCreateUserRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }
}
```

### Repository tests

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_WhenExists_ReturnsUser() {
        User saved = userRepository.save(buildUser());

        Optional<User> found = userRepository.findByEmail(saved.getEmail());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(saved.getEmail());
    }
}
```

**Rules:**

- Unit tests: `@ExtendWith(MockitoExtension.class)` — no Spring context loading
- Controller tests: `@WebMvcTest` — test only the web layer
- Repository tests: `@DataJpaTest` + Testcontainers with real database
- Full integration tests: `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Test method names: `methodName_StateUnderTest_ExpectedBehavior`
- Always use `@DisplayName` with a plain-English description
- Minimum coverage targets: **70% line**, **80% branch** for service layer
- Never use `@Spy` when `@Mock` is enough
- Test builders: extract `buildUser()`, `buildCreateUserRequest()` into a shared `TestFixtures` class

---

## 17. Dependency Injection Rules

```java
// ALWAYS — constructor injection via @RequiredArgsConstructor
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;   // immutable, testable
    private final UserMapper userMapper;
}

// NEVER — field injection
@Service
public class UserServiceImpl {
    @Autowired                            // bad: not testable without Spring context
    private UserRepository userRepository;
}

// NEVER — setter injection (except for optional dependencies)
@Service
public class UserServiceImpl {
    private UserRepository userRepository;

    @Autowired
    public void setUserRepository(UserRepository r) { ... }
}
```

**Rules:**

- Constructor injection always — fields must be `final`
- Use `@RequiredArgsConstructor` (Lombok) — no manual constructor boilerplate
- Circular dependencies are a design smell — restructure, do not use `@Lazy` as a workaround
- `@Bean` methods in `@Configuration` classes always use method parameters for dependencies, not field injection

---

## 18. Transaction Management

```java
@Service
@Transactional(readOnly = true)   // class-level default: all reads are read-only
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    // READ — inherits class-level readOnly = true
    public OrderResponse findById(Long id) { ... }

    // WRITE — override with full transaction
    @Transactional
    public OrderResponse create(CreateOrderRequest request) { ... }

    // WRITE with specific propagation
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAuditLog(AuditEvent event) { ... }

    // NEVER call @Transactional methods from within the same class
    // — Spring AOP proxy is bypassed. Extract to a separate bean instead.
}
```

**Rules:**

- `@Transactional(readOnly = true)` at class level on all service implementations
- Explicit `@Transactional` on all write methods
- Never call a `@Transactional` method from within the same class — proxy is bypassed
- Keep transactions as short as possible — no HTTP calls, no file I/O inside a transaction
- For event publishing after commit, use `@TransactionalEventListener(phase = AFTER_COMMIT)`

---

## 19. Inter-Service Communication

### Feign Client

```java
@FeignClient(
    name = "notification-service",
    url = "${services.notification.url}",
    fallback = NotificationClientFallback.class
)
public interface NotificationClient {

    @PostMapping("/api/v1/notifications/send")
    ApiResponse<Void> sendNotification(@RequestBody SendNotificationRequest request);
}

// Fallback — always implement for resilience
@Component
@Slf4j
public class NotificationClientFallback implements NotificationClient {

    @Override
    public ApiResponse<Void> sendNotification(SendNotificationRequest request) {
        log.error("Notification service unavailable — falling back for request={}", request);
        return ApiResponse.error("SERVICE_UNAVAILABLE", "Notification service is unavailable");
    }
}
```

### Kafka Events

```java
// Event DTO — must be serializable and versioned
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private String eventId;            // UUID
    private String eventType;          // "USER_CREATED"
    private int eventVersion;          // schema version for consumers
    private Long userId;
    private Long tenantId;
    private String email;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;
}

// Publisher
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send("user.created", String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserCreatedEvent for userId={}",
                                  event.getUserId(), ex);
                    } else {
                        log.info("Published UserCreatedEvent for userId={}", event.getUserId());
                    }
                });
    }
}
```

**Rules:**

- Always use Feign with fallback for synchronous inter-service calls
- Always publish Kafka events **after** transaction commit using `@TransactionalEventListener`
- Event DTOs must include `eventId` (UUID), `eventType`, `eventVersion`, `occurredAt`
- Never call another service's database directly — only through that service's API
- Timeouts must be configured for all Feign clients — no unbounded waits

---

## 20. General Code Hygiene

### What to never do

```java
// NEVER — raw types
List users = userRepository.findAll();          // use List<User>

// NEVER — null returns from service methods
public UserResponse findById(Long id) {
    return null;                                 // throw exception or return Optional
}

// NEVER — catching and swallowing exceptions
try {
    someOperation();
} catch (Exception e) {
    // nothing here                              // always log or rethrow
}

// NEVER — business logic in controllers
@PostMapping
public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {  // belongs in service
        return ResponseEntity.status(409).build();
    }
    ...
}

// NEVER — N+1 queries
List<Order> orders = orderRepository.findAll();
orders.forEach(o -> o.getUser().getName());   // triggers N selects — use JOIN FETCH

// NEVER — Optional.get() without check
Optional<User> user = userRepository.findById(id);
user.get().getName();                          // use orElseThrow()
```

### What to always do

```java
// ALWAYS — Optional.orElseThrow
User user = userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));

// ALWAYS — streams over imperative loops for transformation
List<UserResponse> responses = users.stream()
        .map(userMapper::toResponse)
        .collect(Collectors.toList());

// ALWAYS — explicit column fetch with JOIN FETCH to avoid N+1
@Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.tenantId = :tenantId")
List<Order> findAllWithUserByTenantId(@Param("tenantId") Long tenantId);

// ALWAYS — meaningful variable names, no single-letter variables outside tiny lambdas
users.stream()
        .filter(user -> user.getStatus().isActive())   // not .filter(u -> u.getStatus()...)
        .collect(Collectors.toList());

// ALWAYS — early return over deep nesting
public UserResponse findByEmail(String email) {
    if (email == null || email.isBlank()) {
        throw new IllegalArgumentException("Email cannot be blank");
    }
    return userRepository.findByEmail(email)
            .map(userMapper::toResponse)
            .orElseThrow(() -> new UserNotFoundException(email));
}
```

### Code review checklist

Before any code is merged, verify:

- [ ] No entity appears in a controller signature
- [ ] No repository is called directly from a controller
- [ ] All endpoints return `ApiResponse<T>` envelope
- [ ] All write service methods have `@Transactional`
- [ ] All custom exceptions extend from `BaseServiceException`
- [ ] No `@Autowired` field injection anywhere
- [ ] No hardcoded secrets, URLs, or credentials
- [ ] All date/time fields use `LocalDateTime` (not `Date`)
- [ ] All relationships are `FetchType.LAZY`
- [ ] `@JsonInclude(NON_NULL)` on all response DTOs
- [ ] Unit test exists for every public service method
- [ ] No `Optional.get()` without `orElseThrow()`
- [ ] No swallowed exceptions (empty catch blocks)
- [ ] Logging uses structured parameters, not string concatenation
- [ ] No PII or tokens logged at any level

---
