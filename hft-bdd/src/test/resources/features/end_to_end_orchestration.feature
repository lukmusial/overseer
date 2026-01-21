Feature: End-to-End Trading System Orchestration
  As a trading system operator
  I want to verify that all components work together correctly
  So that market signals flow through strategies, risk management, and execution properly

  Background:
    Given the integrated trading engine is initialized
    And the risk engine is configured with standard rules
    And the persistence layer is active

  # ============================================================================
  # SCENARIO GROUP 1: Market Signal → Strategy → Order Flow
  # ============================================================================

  @e2e @signal-to-order
  Scenario: Momentum strategy generates buy order from bullish market signal
    Given I have a momentum strategy for "AAPL" on "ALPACA"
    And the strategy is configured with:
      | parameter       | value |
      | shortPeriod     | 5     |
      | longPeriod      | 10    |
      | signalThreshold | 0.01  |
      | maxPositionSize | 1000  |
    And the strategy is started
    When the market sends an uptrend price sequence:
      | price  |
      | 150.00 |
      | 151.00 |
      | 152.00 |
      | 153.00 |
      | 154.00 |
      | 155.00 |
      | 156.00 |
      | 157.00 |
      | 158.00 |
      | 159.00 |
      | 160.00 |
    Then the strategy should generate a bullish signal
    And an order should be submitted to the engine
    And the risk engine should approve the order
    And the order should be recorded in the audit log

  @e2e @signal-to-order
  Scenario: Mean reversion strategy generates buy order when price drops below mean
    Given I have a mean reversion strategy for "BTCUSDT" on "BINANCE"
    And the strategy is configured with:
      | parameter       | value |
      | lookbackPeriod  | 10    |
      | entryZScore     | 2.0   |
      | exitZScore      | 0.5   |
      | maxPositionSize | 100   |
    And the strategy is started
    When the market establishes a mean price around 50000
    And the price suddenly drops to 48000
    Then the strategy should detect an oversold condition
    And a buy order should be generated
    And the risk engine should approve the order

  # ============================================================================
  # SCENARIO GROUP 2: Risk Management Blocking Orders
  # ============================================================================

  @e2e @risk-rejection
  Scenario: Order rejected when exceeding maximum order size
    Given the risk limits are configured with:
      | limit           | value   |
      | maxOrderSize    | 100     |
      | maxPositionSize | 10000   |
    When I attempt to submit a buy order for 500 shares of "AAPL"
    Then the order should be rejected by the risk engine
    And the rejection reason should contain "exceeds max"
    And the rejection should be recorded in the audit log
    And the circuit breaker should record a failure

  @e2e @risk-rejection
  Scenario: Order rejected when exceeding maximum position size
    Given the risk limits are configured with:
      | limit           | value |
      | maxOrderSize    | 1000  |
      | maxPositionSize | 500   |
    And I already have a position of 400 shares in "AAPL"
    When I attempt to submit a buy order for 200 shares of "AAPL"
    Then the order should be rejected by the risk engine
    And the rejection reason should contain "exceeds max"

  @e2e @risk-rejection
  Scenario: Order rejected when exceeding daily notional limit
    Given the risk limits are configured with:
      | limit             | value     |
      | maxDailyNotional  | 1000000   |
    And I have already traded 900000 in notional value today
    When I attempt to submit a buy order for 1000 shares of "AAPL" at 150.00
    Then the order should be rejected by the risk engine
    And the rejection reason should contain "exceed limit"

  @e2e @risk-rejection
  Scenario: Order rejected when daily loss limit exceeded
    Given the risk limits are configured with:
      | limit         | value  |
      | maxDailyLoss  | 10000  |
    And I have a realized loss of 9500 today
    When I attempt to submit a new order
    Then the order should be rejected by the risk engine
    And the rejection reason should contain "loss limit breached"

  # ============================================================================
  # SCENARIO GROUP 3: Circuit Breaker Behavior
  # ============================================================================

  @e2e @circuit-breaker
  Scenario: Circuit breaker trips after multiple rejections
    Given the circuit breaker threshold is 3 failures
    And the risk limits are configured with:
      | limit        | value |
      | maxOrderSize | 100   |
    When I submit 3 orders that exceed the maximum size
    Then the circuit breaker should be in OPEN state
    And all subsequent orders should be rejected with "Circuit breaker"
    And the circuit breaker trip should be recorded in the audit log

  @e2e @circuit-breaker
  Scenario: Circuit breaker recovers after cooldown period
    Given the circuit breaker is in OPEN state
    And the cooldown period is 100 milliseconds
    When I wait for the cooldown period to elapse
    Then the circuit breaker should be in HALF_OPEN state
    When I submit a valid order
    Then the order should be approved
    And the circuit breaker should be in CLOSED state

  # ============================================================================
  # SCENARIO GROUP 4: Order Fill → Position Update → P&L
  # ============================================================================

  @e2e @fill-to-pnl
  Scenario: Order fill updates position and calculates P&L correctly
    Given I have no position in "AAPL"
    When I submit a buy order for 100 shares of "AAPL" at 150.00
    And the order is approved and filled at 150.00
    Then my position in "AAPL" should be 100 shares
    And the average entry price should be 150.00
    And the unrealized P&L should be 0
    When the current market price updates to 155.00
    Then the unrealized P&L should be 500.00
    When I execute a sell for 100 shares at 155.00
    Then the position should be flat
    And the current realized P&L should be 500.00
    And the trade should be recorded in the trade journal

  @e2e @fill-to-pnl
  Scenario: Multiple fills accumulate position correctly
    Given I have no position in "BTCUSDT"
    When I buy 10 units at 50000
    And I buy 10 more units at 51000
    Then my position should be 20 units
    And the average entry price should be 50500.00
    When I sell 5 units at 52000
    Then my position should be 15 units
    And the position realized P&L should be 7500.00
    And the position manager should track all trades

  # ============================================================================
  # SCENARIO GROUP 5: Strategy + Risk + Position Integration
  # ============================================================================

  @e2e @full-integration
  Scenario: Strategy respects position limits during signal generation
    Given I have a momentum strategy configured with max position 100
    And I currently hold 80 shares of "AAPL"
    And the strategy generates a strong buy signal for 50 shares
    When the strategy attempts to execute the signal
    Then the order quantity should be capped at 20 shares
    And the risk engine should approve the capped order
    And the order should be submitted successfully

  @e2e @full-integration
  Scenario: Strategy stops trading when risk engine is disabled
    Given I have an active momentum strategy
    And the strategy is generating buy signals
    When the risk engine is disabled due to "Manual intervention"
    Then all new orders should be rejected
    And the rejection reason should be "Risk engine disabled"
    And the strategy should receive rejection notifications

  @e2e @full-integration
  Scenario: Complete trading cycle with profit taking
    Given I have a mean reversion strategy for "AAPL"
    And I have no initial position
    # Entry phase - price drops, strategy buys
    When the price drops 2 standard deviations below the mean to 145.00
    Then an entry buy signal should be generated by the strategy
    And a buy order for the full position size should be submitted
    When the order is filled at 145.00
    Then the position should be long
    # Exit phase - price reverts to mean
    When the price reverts to the mean at 150.00
    Then the strategy should generate an exit signal
    And a sell order should be submitted
    When the sell order is filled at 150.00
    Then the position should be flat
    And the realized P&L should be positive
    And all trades should be persisted to the journal

  # ============================================================================
  # SCENARIO GROUP 6: Exposure Monitoring
  # ============================================================================

  @e2e @exposure
  Scenario: Net exposure limit prevents new positions
    Given the risk limits are configured with:
      | limit           | value    |
      | maxNetExposure  | 1000000  |
    And I have long positions totaling 900000 in exposure
    When I attempt to buy 1000 shares of "AAPL" at 150.00
    Then the order should be rejected
    And the rejection reason should contain "exceed limit"

  @e2e @exposure
  Scenario: Gross exposure is tracked across long and short positions
    Given I have a long position of 500 shares in "AAPL" at 150.00
    And I have a short position of 300 shares in "TSLA" at 200.00
    Then my gross exposure should be 135000
    And my net exposure should be 15000
    When I submit a new order
    Then the risk check should use the current exposure values

  # ============================================================================
  # SCENARIO GROUP 7: Persistence and Audit Trail
  # ============================================================================

  @e2e @persistence
  Scenario: All trading events are persisted to audit log
    When the following trading sequence occurs:
      | event              | details                      |
      | engine_started     | Trading engine started       |
      | order_submitted    | BUY 100 AAPL @ 150.00       |
      | order_accepted     | Exchange ID: EX123           |
      | order_filled       | Filled 100 @ 150.00          |
      | position_updated   | AAPL: +100 shares            |
    Then the audit log should contain all events in order
    And each event should have a timestamp
    And the events should be retrievable by date

  @e2e @persistence
  Scenario: Trade journal maintains complete trade history
    Given I execute the following trades:
      | symbol | side | quantity | price  |
      | AAPL   | BUY  | 100      | 150.00 |
      | AAPL   | BUY  | 50       | 151.00 |
      | AAPL   | SELL | 75       | 153.00 |
      | TSLA   | BUY  | 10       | 200.00 |
    Then the trade journal should contain 4 trades
    And the trades for "AAPL" should total 3 records
    And the journal should be queryable by symbol
    And the journal should be queryable by date

  # ============================================================================
  # SCENARIO GROUP 8: Engine State Management
  # ============================================================================

  @e2e @engine-state
  Scenario: Engine state snapshot captures all component states
    Given the engine has processed several orders
    And positions exist for multiple symbols
    When I request an engine snapshot
    Then the snapshot should include:
      | component        | state                    |
      | engine           | running status           |
      | ring_buffer      | capacity and cursor      |
      | order_manager    | total and active orders  |
      | position_manager | all positions            |
      | risk_engine      | counters and limits      |
      | metrics          | latency statistics       |

  @e2e @engine-state
  Scenario: Daily counter reset clears trading limits
    Given the engine has been running all day with:
      | counter              | value   |
      | ordersSubmittedToday | 500     |
      | notionalTradedToday  | 5000000 |
    When the daily reset is triggered
    Then all daily counters should be zero
    And the order metrics should be reset
    And new orders should have fresh daily limits

  # ============================================================================
  # SCENARIO GROUP 9: Multi-Strategy Orchestration
  # ============================================================================

  @e2e @multi-strategy
  Scenario: Multiple strategies can operate on different symbols
    Given I have a momentum strategy for "AAPL"
    And I have a mean reversion strategy for "BTCUSDT"
    And both strategies are started
    When "AAPL" shows an uptrend signal
    And "BTCUSDT" shows an oversold signal
    Then both strategies should generate buy orders
    And both orders should pass risk checks independently
    And positions should be tracked separately per symbol

  @e2e @multi-strategy
  Scenario: Shared risk limits affect all strategies
    Given I have multiple strategies running
    And the total gross exposure limit is 1000000
    When strategy A builds a position of 600000 exposure
    And strategy B attempts to build a position of 500000 exposure
    Then strategy B's order should be rejected
    And the rejection should reference gross exposure limit

  # ============================================================================
  # SCENARIO GROUP 10: Error Recovery
  # ============================================================================

  @e2e @error-recovery
  Scenario: System handles order rejection gracefully
    When an order is rejected by the exchange
    Then the order status should be REJECTED
    And the rejection should be logged
    And the position should remain unchanged
    And the strategy should be notified of the rejection

  @e2e @error-recovery
  Scenario: System recovers from partial fills
    Given I submit a buy order for 100 shares
    When the exchange fills 60 shares at 150.00
    Then my position should be 60 shares
    And the order should be in PARTIALLY_FILLED status
    And the remaining quantity should be 40 shares
    When the remaining 40 shares are filled at 150.50
    Then my position should be 100 shares
    And the order should be FILLED
    And my average fill price should be calculated correctly
