Feature: Trading Strategy Execution
  As a trader
  I want to configure and run trading strategies
  So that I can automate my trading decisions

  Background:
    Given the trading engine is initialized

  @strategy
  Scenario: Create a momentum strategy
    Given I want to trade symbol "AAPL" on exchange "ALPACA"
    When I create a momentum strategy with parameters:
      | parameter      | value |
      | shortPeriod    | 10    |
      | longPeriod     | 30    |
      | signalThreshold| 0.02  |
      | maxPositionSize| 1000  |
    Then the strategy should be created successfully
    And the strategy state should be "INITIALIZED"

  @strategy
  Scenario: Create a mean reversion strategy
    Given I want to trade symbol "BTCUSDT" on exchange "BINANCE"
    When I create a mean reversion strategy with parameters:
      | parameter        | value |
      | lookbackPeriod   | 20    |
      | entryZScore      | 2.0   |
      | exitZScore       | 0.5   |
      | maxPositionSize  | 100   |
    Then the strategy should be created successfully
    And the strategy state should be "INITIALIZED"

  @strategy @risk
  Scenario: Strategy respects maximum position size calculation
    Given I have a momentum strategy with max position 100
    And I currently have a position of 80 shares
    When the strategy generates a buy signal for 50 shares
    Then the actual order quantity should be limited to 20 shares
