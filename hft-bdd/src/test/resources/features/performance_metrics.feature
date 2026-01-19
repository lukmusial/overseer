Feature: Performance Metrics Tracking
  As a system operator
  I want to track comprehensive performance metrics
  So that I can monitor and optimize the trading system

  Background:
    Given the metrics system is initialized

  @performance
  Scenario: Order submission latency tracking
    When I submit 100 orders with varying latencies
    Then the submit latency histogram should contain 100 samples
    And the p50 latency should be calculated
    And the p99 latency should be calculated

  @performance
  Scenario: Order acknowledgment latency tracking
    When 100 orders are acknowledged by the exchange
    Then the ack latency histogram should contain 100 samples
    And the average ack latency should be calculated

  @performance
  Scenario: Order fill latency tracking
    When 100 orders are filled
    Then the fill latency histogram should contain 100 samples
    And the total quantity filled should be recorded
    And the total notional value should be recorded

  @performance
  Scenario: Throughput measurement
    When I submit orders at a rate of 1000 per second for 5 seconds
    Then the measured throughput should be approximately 1000 orders per second

  @performance
  Scenario: Error rate tracking
    Given I submit 100 orders
    And 5 orders are rejected
    And 3 orders timeout
    Then the reject rate should be 5%
    And the timeout count should be 3

  @performance
  Scenario: Metrics snapshot
    Given I have accumulated various metrics
    When I take a metrics snapshot
    Then the snapshot should contain all order counts
    And the snapshot should contain all latency statistics
    And the snapshot should contain all error counts

  @performance
  Scenario: Latency percentile accuracy
    When I record latencies from 1us to 100us in 1us increments
    Then the p50 should be approximately 50us
    And the p90 should be approximately 90us
    And the p99 should be approximately 99us

  @performance
  Scenario: Metrics reset
    Given I have accumulated metrics
    When I reset the metrics
    Then all order counts should be zero
    And all latency histograms should be empty
