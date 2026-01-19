Feature: Order Lifecycle Management
  As a trader
  I want to submit and manage orders
  So that I can execute trading strategies

  Background:
    Given the trading system is initialized
    And the exchange connection is established

  @smoke
  Scenario: Submit a market order successfully
    Given I have a symbol "AAPL" on exchange "ALPACA"
    When I submit a market order to buy 100 shares
    Then the order should be in "SUBMITTED" status
    And the order should have a client order ID

  @smoke
  Scenario: Order acknowledgment from exchange
    Given I have a symbol "AAPL" on exchange "ALPACA"
    And I submit a market order to buy 100 shares
    When the exchange acknowledges the order with ID "EX123"
    Then the order should be in "ACCEPTED" status
    And the acknowledgment latency should be recorded

  Scenario: Order fill
    Given I have a symbol "AAPL" on exchange "ALPACA"
    And I submit a limit order to buy 100 shares at price 150.00
    And the exchange acknowledges the order
    When the order is filled at price 150.00
    Then the order should be in "FILLED" status
    And the fill latency should be recorded
    And the position should show 100 shares

  Scenario: Partial fill
    Given I have a symbol "AAPL" on exchange "ALPACA"
    And I submit a limit order to buy 100 shares at price 150.00
    And the exchange acknowledges the order
    When the order is partially filled with 50 shares at price 150.00
    Then the order should be in "PARTIALLY_FILLED" status
    And the remaining quantity should be 50

  Scenario: Order cancellation
    Given I have a symbol "AAPL" on exchange "ALPACA"
    And I submit a limit order to buy 100 shares at price 150.00
    And the exchange acknowledges the order
    When I cancel the order
    Then the order should be in "CANCELLED" status
    And the cancellation latency should be recorded

  Scenario: Order rejection
    Given I have a symbol "INVALID" on exchange "ALPACA"
    When I submit a market order to buy 100 shares
    And the exchange rejects the order
    Then the order should be in "REJECTED" status
    And the rejection should be recorded in metrics

  @performance
  Scenario: High volume order submission
    Given I have a symbol "AAPL" on exchange "ALPACA"
    When I submit 1000 market orders in quick succession
    Then all orders should have unique client IDs
    And the average submit latency should be less than 100 microseconds
    And the throughput should be recorded
