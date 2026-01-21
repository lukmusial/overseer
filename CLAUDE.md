# HFT Client - Development Guide

## Project Overview
A modular high-frequency trading client in Java supporting:
- **Stocks**: Alpaca (REST/WebSocket API, paper trading)
- **Crypto**: Binance (REST/WebSocket API, testnet)

## Java Setup (Required: Java 21)

This project requires Java 21. The Java home is configured in `gradle.properties`:
- **Intel Mac**: `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- **Apple Silicon**: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`

All `./gradlew` commands will automatically use the configured Java version.

### Installing Java 21 (if not present)
```bash
# Homebrew (recommended)
brew install openjdk@21

# Verify installation
/usr/libexec/java_home -V 2>&1 | grep 21
```

### Switching between Intel/Apple Silicon
Edit `gradle.properties` and update `org.gradle.java.home` to match your architecture.

## Development Workflow (NON-NEGOTIABLE)

**Every functional change MUST follow this workflow:**

1. **Implement the change**
2. **Write tests** - NEW FUNCTIONALITY MUST HAVE TESTS (see Testing Requirements below)
3. **Run tests** - `./gradlew test` and `npm test` (for UI changes) must pass
4. **Update documentation** - if the change affects architecture, update `docs/ARCHITECTURE.md`
5. **Commit to git** - atomic commits with meaningful messages

Never skip tests, documentation updates, or commits. This ensures:
- Code is always in a working state
- Changes are tracked and reversible
- Regressions are caught immediately
- Documentation stays in sync with code

```bash
# Standard workflow for backend changes
./gradlew test && git add -A && git commit -m "Description of change"

# Standard workflow for frontend changes
cd hft-ui && npm test && cd .. && git add -A && git commit -m "Description of change"

# Full stack changes
./gradlew test && cd hft-ui && npm test && cd .. && git add -A && git commit -m "Description of change"
```

### Testing Requirements (MANDATORY)

**All new functionality MUST include corresponding tests.** This is non-negotiable.

#### Backend (Java) Testing

For new/modified code, add tests to the appropriate module's `src/test/java` directory:

| Module | Test Location | Test Type |
|--------|--------------|-----------|
| `hft-core` | `hft-core/src/test/java/com/hft/core/` | Unit tests for domain models |
| `hft-algo` | `hft-algo/src/test/java/com/hft/algo/` | Unit tests for algorithms |
| `hft-api` | `hft-api/src/test/java/com/hft/api/` | Controller tests (MockMvc), Service tests |
| `hft-exchange-*` | `hft-exchange-*/src/test/java/` | Integration tests for exchange adapters |
| `hft-bdd` | `hft-bdd/src/test/resources/features/` | End-to-end BDD scenarios |

**Backend test patterns:**
- Controllers: Use `@WebMvcTest` with `MockMvc` for REST endpoint testing
- Services: Use `@ExtendWith(MockitoExtension.class)` for unit tests
- Integration: Use `@SpringBootTest` for full context tests

```bash
# Run all backend tests
./gradlew test

# Run specific module tests
./gradlew :hft-api:test
./gradlew :hft-algo:test
```

#### Frontend (React/TypeScript) Testing

The UI uses Vitest + React Testing Library. Tests are located alongside components:

| Type | Location | Pattern |
|------|----------|---------|
| Hook tests | `src/hooks/*.test.ts` | Test custom hooks in isolation |
| Component tests | `src/components/*.test.tsx` | Test component behavior and rendering |

**Frontend test patterns:**
- Use `vi.mock()` to mock dependencies (hooks, API calls)
- Use `render()`, `screen`, `fireEvent`, `waitFor` from testing-library
- Test user interactions, not implementation details

```bash
# Run frontend tests
cd hft-ui && npm test

# Run with coverage
cd hft-ui && npm run test:coverage

# Watch mode for development
cd hft-ui && npm run test:watch
```

#### What MUST Be Tested

| Change Type | Required Tests |
|-------------|----------------|
| New REST endpoint | Controller test with MockMvc |
| New service method | Service unit test |
| New React component | Component render + interaction tests |
| New custom hook | Hook unit test |
| New algorithm | Unit tests + BDD scenario |
| Bug fix | Regression test proving fix |

### Documentation Requirements

**Update `docs/ARCHITECTURE.md` when:**
- Adding or removing modules
- Changing component interactions or data flow
- Adding new trading algorithms or strategies
- Modifying risk rules or circuit breaker behavior
- Changing persistence mechanisms
- Altering the event processing pipeline
- Adding new sequence flows (e.g., new order types)

**Documentation uses Mermaid diagrams** - ensure diagrams are updated to reflect changes:
- Architecture diagrams for structural changes
- Sequence diagrams for flow changes
- Class diagrams for new interfaces/implementations
- State diagrams for state machine changes

## Quick Start

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run BDD tests
./gradlew :hft-bdd:test

# Run JMH benchmarks
./gradlew :hft-bdd:jmh

# Start the application
./gradlew :hft-app:bootRun
```

## Project Structure

```
hft-client/
├── docs/               # Architecture documentation with diagrams
│   └── ARCHITECTURE.md # Component diagrams, sequence diagrams, algorithm visuals
├── hft-core/           # Domain models, interfaces (zero dependencies)
├── hft-algo/           # Trading algorithms (VWAP, TWAP, Momentum, Mean Reversion)
├── hft-exchange-api/   # Exchange port interfaces
├── hft-exchange-alpaca/# Alpaca adapter
├── hft-exchange-binance/# Binance adapter
├── hft-risk/           # Risk management module
├── hft-engine/         # Order matching, event processing (Disruptor)
├── hft-persistence/    # Chronicle-based persistence
├── hft-api/            # Spring Boot REST/WebSocket API
├── hft-app/            # Main application assembly
├── hft-bdd/            # Cucumber BDD tests & JMH benchmarks
└── hft-ui/             # React frontend (Vitest + React Testing Library)
```

## Architecture (Hexagonal/Ports & Adapters)

The system follows hexagonal architecture:
- **Domain Core** (`hft-core`): Business logic and models
- **Ports** (`hft-core/port`): Interfaces for external dependencies
- **Adapters** (`hft-exchange-*`): Exchange implementations

## Key Design Principles

### Low-Latency Patterns
1. **Object Pooling**: Use `ObjectPool<T>` for frequently created objects
2. **Primitive Collections**: Use Agrona collections instead of JDK
3. **Off-heap Memory**: Chronicle for persistence
4. **Lock-free Messaging**: LMAX Disruptor ring buffer
5. **GC Tuning**: ZGC with `-XX:+UseZGC -XX:+ZGenerational`

### Core Domain Models
- `Order`: Mutable for pooling, tracks full lifecycle and latency metrics
- `Position`: Tracks quantities, average price, realized/unrealized P&L
- `Quote`: Market data with bid/ask prices and sizes
- `Trade`: Executed fill with commission tracking
- `Symbol`: Immutable identifier for trading instruments

### Metrics System
- `OrderMetrics`: Comprehensive order statistics (counts, latencies, throughput)
- `LatencyHistogram`: Lock-free histogram with percentile support
- All metrics are thread-safe using atomic operations

## Code Conventions

### Prices and Quantities
- All prices stored as `long` in minor units (cents for USD)
- Use `priceScale` field (default 100) for conversions
- Example: `$150.00` stored as `15000L`

### Timestamps
- All timestamps in nanoseconds (epoch nanos)
- Use `System.nanoTime()` for latency measurements
- Use epoch millis for exchange timestamps

### Object Pooling Pattern
```java
ObjectPool<Order> orderPool = new ObjectPool<>(Order::new, 1024);
Order order = orderPool.acquire();
try {
    // Use order
} finally {
    orderPool.release(order);
}
```

## Testing

### Backend Unit Tests
Located in `src/test/java` of each module. Run with:
```bash
./gradlew :hft-core:test
./gradlew :hft-api:test
./gradlew test  # All modules
```

### Frontend Unit Tests (Vitest)
Located in `hft-ui/src/**/*.test.{ts,tsx}`. Run with:
```bash
cd hft-ui && npm test
cd hft-ui && npm run test:coverage
```

### BDD Tests (Cucumber)
Feature files in `hft-bdd/src/test/resources/features/`
Step definitions in `hft-bdd/src/test/java/com/hft/bdd/steps/`

Key scenarios:
- Order lifecycle management
- Position tracking
- Performance metrics validation

### Performance Benchmarks (JMH)
Benchmarks in `hft-bdd/src/test/java/com/hft/bdd/benchmark/`

Key benchmarks:
- `OrderBenchmark`: Order creation and lifecycle
- `MetricsBenchmark`: Metrics recording overhead
- `PositionBenchmark`: P&L calculations
- `QuoteBenchmark`: Quote processing

## Performance Metrics Captured

### Order Metrics
- Orders submitted/accepted/filled/cancelled/rejected counts
- Submit latency (order creation to network send)
- Ack latency (send to exchange acknowledgment)
- Fill latency (send to execution)
- Round-trip latency (complete order lifecycle)
- Throughput (orders per second)

### Latency Statistics
- Min, max, mean
- Percentiles: p50, p90, p95, p99, p99.9
- All values in nanoseconds

## JVM Configuration

### Development
```bash
-XX:+UseZGC -XX:+ZGenerational -Xms1g -Xmx1g
```

### Production
```bash
-XX:+UseZGC
-XX:+ZGenerational
-XX:+AlwaysPreTouch
-XX:+UseNUMA
-XX:+DisableExplicitGC
-Xms4g
-Xmx4g
-Djava.lang.Integer.IntegerCache.high=65536
```

## Dependencies

### Core
- LMAX Disruptor 4.0.0 (lock-free messaging)
- Agrona 1.21.1 (primitive collections)
- Chronicle Wire 2.25.0 (zero-GC serialization)
- OkHttp 4.12.0 (HTTP/WebSocket)

### Testing (Backend)
- JUnit 5.10.2
- Mockito 5.10.0
- Cucumber 7.15.0
- JMH 1.37
- Spring Boot Test (MockMvc)

### Testing (Frontend)
- Vitest 4.x
- React Testing Library
- @testing-library/jest-dom
- @testing-library/user-event

## Common Tasks

### Adding a new trading algorithm
1. Create class in `hft-algo/src/main/java/com/hft/algo/`
2. Implement algorithm interface
3. Add unit tests
4. Add BDD scenarios in `hft-bdd`

### Adding exchange support
1. Create new module `hft-exchange-{name}`
2. Implement `ExchangePort`, `OrderPort`, `MarketDataPort`
3. Add to `hft-app` dependencies
4. Add integration tests

### Running benchmarks
```bash
# All benchmarks
./gradlew :hft-bdd:jmh

# Specific benchmark
java -jar hft-bdd/build/libs/hft-bdd-*-jmh.jar OrderBenchmark
```
