# HFT Client Architecture Documentation

This document provides visual documentation of the HFT trading system architecture, including component interactions, sequence diagrams for main operations, and explanations of trading algorithms.

## Table of Contents

1. [System Architecture Overview](#system-architecture-overview)
2. [Module Structure](#module-structure)
3. [Component Interactions](#component-interactions)
4. [Sequence Diagrams](#sequence-diagrams)
5. [Trading Algorithms](#trading-algorithms)
6. [Risk Management](#risk-management)
7. [Persistence Layer](#persistence-layer)

---

## System Architecture Overview

The system follows **Hexagonal Architecture** (Ports & Adapters) with a high-performance event-driven core using LMAX Disruptor.

```mermaid
graph TB
    subgraph "External Clients"
        WEB[Web UI]
        REST[REST API Clients]
        WS[WebSocket Clients]
    end

    subgraph "API Layer"
        API[Spring Boot API<br/>hft-api]
        WSH[WebSocket Handler]
    end

    subgraph "Application Services"
        TS[TradingService]
        SM[StrategyManager]
    end

    subgraph "Domain Core"
        subgraph "Trading Engine (hft-engine)"
            TE[TradingEngine<br/>LMAX Disruptor]
            OM[OrderManager]
            PM[PositionManager]
            RB[(Ring Buffer<br/>64K events)]
        end

        subgraph "Algorithms (hft-algo)"
            VWAP[VWAP Algorithm]
            TWAP[TWAP Algorithm]
            MOM[Momentum Strategy]
            MR[Mean Reversion Strategy]
        end

        subgraph "Risk Management (hft-risk)"
            RE[RiskEngine]
            CB[CircuitBreaker]
            RR[Risk Rules]
        end
    end

    subgraph "Infrastructure Adapters"
        subgraph "Exchange Adapters"
            ALP[Alpaca Adapter<br/>hft-exchange-alpaca]
            BIN[Binance Adapter<br/>hft-exchange-binance]
        end

        subgraph "Persistence (hft-persistence)"
            PER[PersistenceManager]
            CQ[(Chronicle Queue)]
        end
    end

    subgraph "External Systems"
        ALPACA[Alpaca API]
        BINANCE[Binance API]
    end

    WEB --> API
    REST --> API
    WS --> WSH

    API --> TS
    WSH --> TS
    TS --> SM
    TS --> TE

    SM --> VWAP
    SM --> TWAP
    SM --> MOM
    SM --> MR

    TE --> RB
    TE --> OM
    TE --> PM
    TE --> RE

    RE --> CB
    RE --> RR

    TE --> ALP
    TE --> BIN
    TE --> PER

    ALP --> ALPACA
    BIN --> BINANCE
    PER --> CQ

    VWAP --> TE
    TWAP --> TE
    MOM --> TE
    MR --> TE
```

---

## Module Structure

```mermaid
graph LR
    subgraph "Core Layer (No External Dependencies)"
        CORE[hft-core<br/>Domain Models<br/>Port Interfaces]
    end

    subgraph "Business Logic Layer"
        ALGO[hft-algo<br/>Trading Algorithms]
        RISK[hft-risk<br/>Risk Management]
        ENGINE[hft-engine<br/>Event Processing]
    end

    subgraph "Infrastructure Layer"
        EXAPI[hft-exchange-api<br/>Exchange Interfaces]
        ALPACA[hft-exchange-alpaca]
        BINANCE[hft-exchange-binance]
        PERSIST[hft-persistence<br/>Chronicle Queue]
    end

    subgraph "Application Layer"
        API[hft-api<br/>REST/WebSocket]
        APP[hft-app<br/>Main Application]
    end

    subgraph "Testing"
        BDD[hft-bdd<br/>Cucumber & JMH]
    end

    CORE --> ALGO
    CORE --> RISK
    CORE --> ENGINE
    CORE --> EXAPI
    CORE --> PERSIST

    EXAPI --> ALPACA
    EXAPI --> BINANCE

    ALGO --> ENGINE
    RISK --> ENGINE
    PERSIST --> ENGINE

    ENGINE --> API
    ALPACA --> APP
    BINANCE --> APP
    API --> APP
    PERSIST --> APP

    APP --> BDD
```

### Module Responsibilities

| Module | Responsibility |
|--------|---------------|
| `hft-core` | Domain models (Order, Quote, Position, Trade), port interfaces |
| `hft-algo` | Trading algorithms (VWAP, TWAP) and strategies (Momentum, Mean Reversion) |
| `hft-risk` | Risk engine, circuit breaker, configurable risk rules |
| `hft-engine` | LMAX Disruptor event processing, order/position management |
| `hft-exchange-api` | Exchange port interface definitions |
| `hft-exchange-alpaca` | Alpaca REST/WebSocket adapter |
| `hft-exchange-binance` | Binance REST/WebSocket adapter |
| `hft-persistence` | Chronicle Queue based persistence |
| `hft-api` | Spring Boot REST and WebSocket API |
| `hft-app` | Application assembly and configuration |
| `hft-bdd` | Cucumber BDD tests and JMH benchmarks |

---

## Component Interactions

### Event-Driven Architecture

```mermaid
graph TB
    subgraph "Event Producers"
        API[API Layer]
        MD[Market Data Feed]
        EX[Exchange Callbacks]
    end

    subgraph "LMAX Disruptor"
        RB[(Ring Buffer<br/>64K Events)]

        subgraph "Event Handlers (Pipeline)"
            OH[OrderHandler]
            PH[PositionHandler]
            MH[MetricsHandler]
        end
    end

    subgraph "Event Consumers"
        OM[OrderManager]
        PM[PositionManager]
        METRICS[OrderMetrics]
        STRAT[Strategies]
    end

    API -->|NEW_ORDER| RB
    MD -->|QUOTE_UPDATE| RB
    EX -->|ORDER_ACCEPTED<br/>ORDER_FILLED| RB

    RB --> OH
    OH --> PH
    PH --> MH

    OH --> OM
    PH --> PM
    MH --> METRICS
    RB -.->|Quote Events| STRAT
```

### Event Types

```mermaid
classDiagram
    class TradingEvent {
        +EventType type
        +Order order
        +Quote quote
        +Trade trade
        +populateNewOrder(Order)
        +populateOrderAccepted(Order)
        +populateOrderFilled(Order, qty, price)
        +populateQuoteUpdate(Quote)
        +populateTradeUpdate(Trade)
    }

    class EventType {
        <<enumeration>>
        NEW_ORDER
        CANCEL_ORDER
        MODIFY_ORDER
        ORDER_ACCEPTED
        ORDER_REJECTED
        ORDER_FILLED
        ORDER_CANCELLED
        QUOTE_UPDATE
        TRADE_UPDATE
    }

    TradingEvent --> EventType
```

---

## Sequence Diagrams

### Order Submission Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as TradingService
    participant RE as RiskEngine
    participant TE as TradingEngine
    participant RB as Ring Buffer
    participant OH as OrderHandler
    participant EX as Exchange
    participant PER as Persistence

    Client->>API: submitOrder(request)
    API->>API: Create Order model
    API->>TE: submitOrder(order)

    rect rgb(255, 240, 240)
        Note over TE,RE: Pre-Trade Risk Check (Synchronous)
        TE->>RE: checkPreTrade(order)
        RE->>RE: Check CircuitBreaker
        RE->>RE: Run Risk Rules
        alt Order Rejected
            RE-->>TE: RiskCheckResult(rejected, reason)
            TE->>PER: logOrderRejected()
            TE-->>API: Return rejection reason
            API-->>Client: 400 Bad Request
        end
        RE-->>TE: RiskCheckResult(approved)
    end

    TE->>RB: publishEvent(NEW_ORDER)
    TE-->>API: Return orderId
    API-->>Client: 200 OK (orderId)

    rect rgb(240, 255, 240)
        Note over RB,EX: Async Processing
        RB->>OH: onEvent(NEW_ORDER)
        OH->>OH: Mark order SUBMITTED
        OH->>EX: submitOrder(order)
        EX-->>OH: Async response
    end

    EX->>TE: onOrderAccepted(exchangeOrderId)
    TE->>RB: publishEvent(ORDER_ACCEPTED)
    TE->>PER: saveOrder(order)
```

### Order Fill Processing

```mermaid
sequenceDiagram
    participant EX as Exchange
    participant TE as TradingEngine
    participant RB as Ring Buffer
    participant PH as PositionHandler
    participant PM as PositionManager
    participant RE as RiskEngine
    participant PER as Persistence
    participant STRAT as Strategy

    EX->>TE: onOrderFilled(order, fillQty, fillPrice)

    TE->>TE: Create Trade record
    TE->>RB: publishEvent(ORDER_FILLED)

    RB->>PH: onEvent(ORDER_FILLED)

    rect rgb(240, 248, 255)
        Note over PH,PM: Position Update
        PH->>PM: applyTrade(trade)
        PM->>PM: Update quantity
        PM->>PM: Update averageEntryPrice
        PM->>PM: Calculate realizedPnl
    end

    rect rgb(255, 248, 240)
        Note over TE,RE: Risk Tracking
        TE->>RE: recordFill(symbol, side, qty, price)
        RE->>RE: Update notionalTradedToday
    end

    rect rgb(248, 255, 240)
        Note over TE,PER: Persistence
        TE->>PER: recordTrade(trade)
        TE->>PER: saveOrder(order)
    end

    PH->>STRAT: onFill(trade)
    STRAT->>STRAT: Update position tracking
```

### Market Data Flow

```mermaid
sequenceDiagram
    participant WS as Exchange WebSocket
    participant MD as MarketDataPort
    participant TE as TradingEngine
    participant RB as Ring Buffer
    participant PM as PositionManager
    participant STRAT as Strategy

    WS->>MD: Quote message (JSON)
    MD->>MD: Decode to Quote model
    MD->>TE: onQuoteUpdate(quote)
    TE->>RB: publishEvent(QUOTE_UPDATE)

    par Position P&L Update
        RB->>PM: updateMarketValue(symbol, midPrice)
        PM->>PM: Calculate unrealizedPnl
    and Strategy Signal Generation
        RB->>STRAT: onQuote(quote)
        STRAT->>STRAT: calculateSignal(symbol, quote)
        STRAT->>STRAT: calculateTargetPosition(signal)

        opt Signal Threshold Crossed
            STRAT->>TE: submitOrder(order)
        end
    end
```

### Risk Check Flow

```mermaid
sequenceDiagram
    participant TE as TradingEngine
    participant RE as RiskEngine
    participant CB as CircuitBreaker
    participant Rules as Risk Rules
    participant CTX as RiskContext

    TE->>RE: checkPreTrade(order)

    RE->>RE: Check if enabled
    alt Engine Disabled
        RE-->>TE: Rejected("Engine disabled")
    end

    RE->>CB: checkAllowed()
    alt Circuit Breaker Open
        CB-->>RE: "Circuit breaker open"
        RE-->>TE: Rejected("Circuit breaker open")
    end

    loop For each Rule (by priority)
        RE->>Rules: check(order, context)
        Rules->>CTX: getPosition(symbol)
        Rules->>CTX: getNetExposure()
        Rules->>CTX: getDailyNotional()

        alt Rule Failed
            Rules-->>RE: Rejected(reason)
            RE->>CB: recordFailure(reason)
            RE-->>TE: Rejected(reason)
        end
        Rules-->>RE: Approved
    end

    RE->>RE: Increment ordersApproved
    RE-->>TE: Approved
```

---

## Trading Algorithms

### Algorithm Class Hierarchy

```mermaid
classDiagram
    class TradingAlgorithm {
        <<interface>>
        +getId() String
        +getName() String
        +getState() AlgorithmState
        +initialize(AlgorithmContext)
        +start()
        +pause()
        +resume()
        +cancel()
        +onQuote(Quote)
        +onFill(Trade)
        +onTimer(long)
        +getProgress() double
    }

    class TradingStrategy {
        <<interface>>
        +getSymbols() Set~Symbol~
        +getSignal(Symbol) double
        +getTargetPosition(Symbol) long
        +getCurrentPosition(Symbol) long
        +getRealizedPnl() long
        +getUnrealizedPnl() long
    }

    class ExecutionAlgorithm {
        <<interface>>
        +getTotalQuantity() long
        +getFilledQuantity() long
        +getRemainingQuantity() long
        +getAveragePrice() long
    }

    class AbstractTradingStrategy {
        #currentPositions Map
        #targetPositions Map
        #signals Map
        #calculateSignal(Symbol, Quote)* double
        #calculateTargetPosition(Symbol, double)* long
        #executeTowardsTarget(Symbol)
    }

    class MomentumStrategy {
        -shortPeriod int
        -longPeriod int
        -signalThreshold double
        +calculateSignal() double
    }

    class MeanReversionStrategy {
        -lookbackPeriod int
        -entryZScore double
        -exitZScore double
        +calculateSignal() double
    }

    class TwapAlgorithm {
        -sliceIntervalNanos long
        -totalSlices int
        -quantityPerSlice long
        +onTimer(long)
    }

    class VwapAlgorithm {
        -volumeProfile double[]
        -targetParticipation double
        +onQuote(Quote)
    }

    TradingAlgorithm <|-- TradingStrategy
    TradingAlgorithm <|-- ExecutionAlgorithm
    TradingStrategy <|.. AbstractTradingStrategy
    AbstractTradingStrategy <|-- MomentumStrategy
    AbstractTradingStrategy <|-- MeanReversionStrategy
    ExecutionAlgorithm <|.. TwapAlgorithm
    ExecutionAlgorithm <|.. VwapAlgorithm
```

### VWAP (Volume-Weighted Average Price) Algorithm

VWAP executes large orders by tracking the market's volume profile and participating proportionally.

```mermaid
graph TB
    subgraph "VWAP Concept"
        VP[Historical Volume Profile]
        MP[Market Participation]
        EX[Order Execution]
    end

    VP -->|"Weight by<br/>time period"| MP
    MP -->|"Submit orders<br/>proportionally"| EX

    subgraph "Volume Profile (Example Day)"
        H1[9:30-10:00<br/>High Volume 15%]
        H2[10:00-11:00<br/>Medium 10%]
        H3[11:00-12:00<br/>Low 5%]
        H4[12:00-13:00<br/>Low 5%]
        H5[13:00-14:00<br/>Medium 8%]
        H6[14:00-15:00<br/>High 12%]
        H7[15:00-16:00<br/>Very High 20%]
    end
```

**VWAP Execution Logic:**

```mermaid
sequenceDiagram
    participant VWAP as VwapAlgorithm
    participant CTX as AlgorithmContext
    participant MKT as Market

    Note over VWAP: Initialize with 10,000 shares to buy

    loop Every Quote Update
        MKT->>VWAP: onQuote(quote)
        VWAP->>VWAP: Get current time bucket
        VWAP->>VWAP: Calculate target % for this period
        VWAP->>VWAP: Calculate shares to execute

        Note over VWAP: Target = TotalQty × VolumeProfile[bucket]
        Note over VWAP: e.g., 10,000 × 15% = 1,500 shares

        alt Have shares to execute
            VWAP->>VWAP: Check participation rate
            Note over VWAP: Don't exceed 25% of volume
            VWAP->>CTX: submitOrder(limitOrder)
        end
    end
```

**VWAP Formula:**

```
VWAP = Σ(Price × Volume) / Σ(Volume)

Target Execution = TotalQuantity × VolumeProfile[currentPeriod]
Participation Rate = min(OrderQuantity / AvailableVolume, MaxParticipation)
```

### TWAP (Time-Weighted Average Price) Algorithm

TWAP divides an order into equal time slices, executing evenly throughout the period.

```mermaid
graph TB
    subgraph "TWAP Concept"
        TO[Total Order<br/>10,000 shares]

        subgraph "Time Slices (Equal Division)"
            S1[Slice 1<br/>1,000 shares]
            S2[Slice 2<br/>1,000 shares]
            S3[Slice 3<br/>1,000 shares]
            S4[...]
            S10[Slice 10<br/>1,000 shares]
        end

        TO --> S1
        TO --> S2
        TO --> S3
        TO --> S4
        TO --> S10
    end

    subgraph "Timeline"
        T1[T+0 min]
        T2[T+6 min]
        T3[T+12 min]
        T4[...]
        T10[T+54 min]
    end

    S1 --> T1
    S2 --> T2
    S3 --> T3
    S4 --> T4
    S10 --> T10
```

**TWAP Execution Logic:**

```mermaid
sequenceDiagram
    participant Timer
    participant TWAP as TwapAlgorithm
    participant CTX as AlgorithmContext

    Note over TWAP: Config: 10,000 shares over 60 min<br/>Slice interval: 6 min<br/>Slices: 10<br/>Qty per slice: 1,000

    loop Every Timer Tick
        Timer->>TWAP: onTimer(currentTimeNanos)
        TWAP->>TWAP: Calculate current slice number

        alt New Slice Started
            TWAP->>TWAP: Check if behind schedule

            opt Behind Schedule (Catch-up)
                Note over TWAP: Increase slice quantity
            end

            TWAP->>TWAP: Calculate order price
            Note over TWAP: Use limit price near mid
            TWAP->>CTX: submitOrder(sliceOrder)
            TWAP->>TWAP: Increment currentSlice
        end
    end

    Note over TWAP: Progress = filledQty / totalQty × 100%
```

**TWAP Formula:**

```
SliceQuantity = TotalQuantity / NumberOfSlices
SliceInterval = TotalDuration / NumberOfSlices
CatchUpQuantity = max(0, ExpectedFilled - ActualFilled)
```

### Momentum Strategy

Momentum strategy follows trends by buying assets with upward price momentum and selling those with downward momentum.

```mermaid
graph TB
    subgraph "Momentum Signal Generation"
        PRICE[Price Data]

        subgraph "Moving Averages"
            SMA[Short MA<br/>e.g., 10 periods]
            LMA[Long MA<br/>e.g., 30 periods]
        end

        PRICE --> SMA
        PRICE --> LMA

        CROSS{MA Crossover<br/>Detection}
        SMA --> CROSS
        LMA --> CROSS

        subgraph "Signals"
            BUY[BUY Signal<br/>Short MA > Long MA]
            SELL[SELL Signal<br/>Short MA < Long MA]
            HOLD[HOLD<br/>No significant cross]
        end

        CROSS -->|Golden Cross| BUY
        CROSS -->|Death Cross| SELL
        CROSS -->|Within threshold| HOLD
    end
```

**Momentum Signal Visualization:**

```mermaid
xychart-beta
    title "Momentum Strategy - MA Crossover"
    x-axis [T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]
    y-axis "Price" 100 --> 160
    line [105, 110, 115, 125, 130, 135, 140, 138, 135, 130, 125, 120]
    line [108, 110, 112, 115, 120, 125, 130, 133, 135, 134, 132, 128]
```

*Blue: Price, Orange: Long MA - Buy when price crosses above MA, Sell when below*

**Momentum Strategy Logic:**

```mermaid
sequenceDiagram
    participant MKT as Market Data
    participant MOM as MomentumStrategy
    participant TE as TradingEngine

    MKT->>MOM: onQuote(quote)
    MOM->>MOM: Update price history
    MOM->>MOM: Calculate Short MA (10 periods)
    MOM->>MOM: Calculate Long MA (30 periods)

    MOM->>MOM: Calculate momentum signal
    Note over MOM: signal = (shortMA - longMA) / longMA

    alt signal > threshold (e.g., 0.02)
        MOM->>MOM: Set signal = +1.0 (BULLISH)
        MOM->>MOM: targetPosition = +maxPositionSize
    else signal < -threshold
        MOM->>MOM: Set signal = -1.0 (BEARISH)
        MOM->>MOM: targetPosition = -maxPositionSize
    else Within threshold
        MOM->>MOM: Set signal = 0 (NEUTRAL)
        MOM->>MOM: targetPosition = 0
    end

    MOM->>MOM: delta = targetPosition - currentPosition

    alt delta > 0
        MOM->>TE: submitOrder(BUY, delta)
    else delta < 0
        MOM->>TE: submitOrder(SELL, -delta)
    end
```

**Momentum Formula:**

```
ShortMA = Σ(Price[t-shortPeriod:t]) / shortPeriod
LongMA = Σ(Price[t-longPeriod:t]) / longPeriod

MomentumSignal = (ShortMA - LongMA) / LongMA

if MomentumSignal > threshold:   signal = +1.0 (BUY)
if MomentumSignal < -threshold:  signal = -1.0 (SELL)
else:                            signal = 0.0 (HOLD)
```

### Mean Reversion Strategy

Mean reversion assumes prices will revert to their historical mean, buying when prices are unusually low and selling when unusually high.

```mermaid
graph TB
    subgraph "Mean Reversion Concept"
        PRICE[Current Price]
        MEAN[Historical Mean<br/>Moving Average]
        STD[Standard Deviation]

        PRICE --> ZSCORE
        MEAN --> ZSCORE
        STD --> ZSCORE

        ZSCORE[Z-Score Calculation]

        subgraph "Trading Zones"
            OVERBOUGHT[OVERBOUGHT<br/>Z > +2σ<br/>SELL Signal]
            NORMAL[NORMAL ZONE<br/>-0.5σ to +0.5σ<br/>EXIT positions]
            OVERSOLD[OVERSOLD<br/>Z < -2σ<br/>BUY Signal]
        end

        ZSCORE -->|High Z-Score| OVERBOUGHT
        ZSCORE -->|Near Zero| NORMAL
        ZSCORE -->|Low Z-Score| OVERSOLD
    end
```

**Mean Reversion Bands:**

```mermaid
xychart-beta
    title "Mean Reversion - Bollinger Band Style"
    x-axis [T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]
    y-axis "Price" 90 --> 130
    line [100, 105, 115, 120, 118, 112, 105, 95, 92, 98, 105, 110]
    line [108, 108, 108, 108, 108, 108, 108, 108, 108, 108, 108, 108]
    line [120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120]
    line [96, 96, 96, 96, 96, 96, 96, 96, 96, 96, 96, 96]
```

*Blue: Price, Orange: Mean, Green: Upper Band (+2σ), Red: Lower Band (-2σ)*

**Mean Reversion Strategy Logic:**

```mermaid
sequenceDiagram
    participant MKT as Market Data
    participant MR as MeanReversionStrategy
    participant TE as TradingEngine

    MKT->>MR: onQuote(quote)
    MR->>MR: Update price history (lookback = 20)
    MR->>MR: Calculate Mean (SMA)
    MR->>MR: Calculate Standard Deviation

    MR->>MR: Calculate Z-Score
    Note over MR: Z = (price - mean) / stdDev

    alt Z-Score < -entryZScore (e.g., -2.0)
        Note over MR: Price is 2σ below mean - OVERSOLD
        MR->>MR: signal = +1.0 (BUY)
        MR->>MR: targetPosition = +maxPositionSize
    else Z-Score > +entryZScore (e.g., +2.0)
        Note over MR: Price is 2σ above mean - OVERBOUGHT
        MR->>MR: signal = -1.0 (SELL)
        MR->>MR: targetPosition = -maxPositionSize
    else |Z-Score| < exitZScore (e.g., 0.5)
        Note over MR: Price returned to mean - EXIT
        MR->>MR: signal = 0
        MR->>MR: targetPosition = 0
    else
        Note over MR: Between entry and exit - HOLD
        MR->>MR: Keep current position
    end

    MR->>MR: delta = targetPosition - currentPosition

    alt delta != 0
        MR->>TE: submitOrder(side, |delta|)
    end
```

**Mean Reversion Formulas:**

```
Mean = Σ(Price[t-lookback:t]) / lookback
StdDev = √(Σ(Price - Mean)² / lookback)

Z-Score = (CurrentPrice - Mean) / StdDev

Entry Condition (BUY):  Z-Score < -entryZScore  (e.g., -2.0)
Entry Condition (SELL): Z-Score > +entryZScore  (e.g., +2.0)
Exit Condition:         |Z-Score| < exitZScore  (e.g., 0.5)
```

---

## Risk Management

### Risk Engine Architecture

```mermaid
graph TB
    subgraph "RiskEngine"
        ENABLED[Enabled Flag]
        RULES[Risk Rules Chain]
        CB[Circuit Breaker]
        COUNTERS[Daily Counters]
    end

    subgraph "Risk Rules (Priority Order)"
        R1[1. MaxOrderSizeRule]
        R2[2. MaxOrderNotionalRule]
        R3[3. MaxPositionSizeRule]
        R4[4. MaxNetExposureRule]
        R5[5. MaxGrossExposureRule]
        R6[6. MaxDailyOrdersRule]
        R7[7. MaxDailyNotionalRule]
        R8[8. MaxDailyLossRule]
    end

    subgraph "RiskContext (State)"
        POS[Position Data]
        PNL[P&L Data]
        EXP[Exposure Data]
        DAILY[Daily Counters]
    end

    ORDER[Incoming Order] --> ENABLED
    ENABLED -->|Disabled| REJECT1[Reject: Disabled]
    ENABLED -->|Enabled| CB
    CB -->|Open| REJECT2[Reject: Circuit Breaker]
    CB -->|Closed| RULES

    RULES --> R1
    R1 --> R2
    R2 --> R3
    R3 --> R4
    R4 --> R5
    R5 --> R6
    R6 --> R7
    R7 --> R8
    R8 --> APPROVE[Approved]

    R1 -.->|Query| POS
    R3 -.->|Query| POS
    R4 -.->|Query| EXP
    R5 -.->|Query| EXP
    R6 -.->|Query| DAILY
    R7 -.->|Query| DAILY
    R8 -.->|Query| PNL
```

### Circuit Breaker State Machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED: Initialize

    CLOSED --> OPEN: Failure threshold exceeded
    CLOSED --> CLOSED: Success / Under threshold

    OPEN --> HALF_OPEN: Cooldown period elapsed
    OPEN --> OPEN: Within cooldown

    HALF_OPEN --> CLOSED: Test order succeeds
    HALF_OPEN --> OPEN: Test order fails

    note right of CLOSED: Normal operation<br/>All orders processed
    note right of OPEN: Trading halted<br/>All orders rejected
    note right of HALF_OPEN: Testing recovery<br/>Limited orders allowed
```

### Risk Limits Configuration

```mermaid
classDiagram
    class RiskLimits {
        +long maxOrderSize
        +long maxOrderNotional
        +long maxPositionSize
        +long maxNetExposure
        +long maxGrossExposure
        +long maxDailyOrders
        +long maxDailyNotional
        +long maxDailyLoss
        +defaults() RiskLimits
        +conservative() RiskLimits
        +forTesting() RiskLimits
    }

    class DefaultLimits {
        maxOrderSize = 10,000
        maxOrderNotional = 1,000,000
        maxPositionSize = 50,000
        maxNetExposure = 5,000,000
        maxGrossExposure = 10,000,000
        maxDailyOrders = 10,000
        maxDailyNotional = 50,000,000
        maxDailyLoss = 100,000
    }

    class ConservativeLimits {
        maxOrderSize = 1,000
        maxOrderNotional = 100,000
        maxPositionSize = 5,000
        maxNetExposure = 500,000
        maxGrossExposure = 1,000,000
        maxDailyOrders = 1,000
        maxDailyNotional = 5,000,000
        maxDailyLoss = 10,000
    }

    RiskLimits <|-- DefaultLimits
    RiskLimits <|-- ConservativeLimits
```

---

## Persistence Layer

### Chronicle Queue Architecture

```mermaid
graph TB
    subgraph "PersistenceManager"
        PM[PersistenceManager]
    end

    subgraph "Chronicle Queue Stores"
        TJ[ChronicleTradeJournal]
        OR[ChronicleOrderRepository]
        AL[ChronicleAuditLog]
    end

    subgraph "Wire Format (Serialization)"
        TW[TradeWire]
        OW[OrderWire]
        AW[AuditEventWire]
    end

    subgraph "Memory-Mapped Files"
        TF[(trades/<br/>20260119F.cq4)]
        OF[(orders/<br/>20260119F.cq4)]
        AF[(audit/<br/>20260119F.cq4)]
    end

    PM --> TJ
    PM --> OR
    PM --> AL

    TJ --> TW
    OR --> OW
    AL --> AW

    TW --> TF
    OW --> OF
    AW --> AF

    subgraph "Features"
        F1[Zero-GC Writes]
        F2[Memory-Mapped I/O]
        F3[Daily Rolling Files]
        F4[Replay Capability]
    end
```

### Persistence Flow

```mermaid
sequenceDiagram
    participant TE as TradingEngine
    participant PM as PersistenceManager
    participant TJ as TradeJournal
    participant AL as AuditLog
    participant CQ as Chronicle Queue

    Note over TE,CQ: Order Submission
    TE->>PM: logOrderSubmitted(order)
    PM->>AL: log(ORDER_SUBMITTED, details)
    AL->>CQ: writeDocument(AuditEventWire)

    Note over TE,CQ: Order Fill
    TE->>PM: recordTrade(trade)
    PM->>TJ: record(trade)
    TJ->>CQ: writeDocument(TradeWire)
    PM->>AL: log(ORDER_FILLED, details)
    AL->>CQ: writeDocument(AuditEventWire)

    Note over TE,CQ: Query (e.g., for replay)
    TE->>PM: getTradeJournal()
    PM-->>TE: tradeJournal
    TE->>TJ: getTradesForDate(20260119)
    TJ->>CQ: createTailer()
    CQ-->>TJ: Iterate documents
    TJ-->>TE: List<Trade>
```

### Audit Event Types

```mermaid
graph LR
    subgraph "Audit Event Categories"
        subgraph "Engine Events"
            E1[ENGINE_STARTED]
            E2[ENGINE_STOPPED]
        end

        subgraph "Strategy Events"
            S1[STRATEGY_STARTED]
            S2[STRATEGY_STOPPED]
        end

        subgraph "Order Events"
            O1[ORDER_SUBMITTED]
            O2[ORDER_REJECTED]
            O3[ORDER_FILLED]
        end

        subgraph "Risk Events"
            R1[RISK_CHECK_FAILED]
            R2[CIRCUIT_BREAKER_TRIPPED]
            R3[TRADING_DISABLED]
        end

        subgraph "Position Events"
            P1[POSITION_UPDATED]
        end

        subgraph "System Events"
            SYS[ERROR]
        end
    end
```

---

## Summary

This HFT trading system provides:

1. **Ultra-Low Latency**: LMAX Disruptor with 64K ring buffer, zero-allocation object pooling
2. **Comprehensive Risk Management**: Pluggable rules, circuit breaker, daily limits
3. **Multi-Exchange Support**: Alpaca (stocks) and Binance (crypto) adapters
4. **Advanced Algorithms**: VWAP, TWAP execution; Momentum, Mean Reversion strategies
5. **Complete Audit Trail**: Chronicle Queue based zero-GC persistence
6. **Real-Time Position Tracking**: P&L, exposure, and drawdown calculations
7. **Event-Driven Architecture**: Clean separation of concerns with hexagonal design

The architecture balances:
- **Performance**: Nanosecond latency, lock-free structures
- **Safety**: Pre-trade risk checks, circuit breakers
- **Flexibility**: Pluggable components, multiple exchange support
- **Compliance**: Complete audit logging, position tracking
