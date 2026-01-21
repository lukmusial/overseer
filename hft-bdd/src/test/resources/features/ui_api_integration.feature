Feature: UI and API Integration
  As a trading system operator
  I want the web UI and REST API to function correctly
  So that I can monitor and control the trading system through a browser

  Background:
    Given the trading application is running

  # ============================================================================
  # SCENARIO GROUP 1: Static UI Resources
  # ============================================================================

  @ui @static-resources
  Scenario: UI dashboard is served at root URL
    When I request the root URL "/"
    Then the response status should be 200
    And the response content type should contain "text/html"
    And the response body should contain "HFT Trading Dashboard"

  @ui @static-resources
  Scenario: JavaScript assets are served correctly
    When I request the assets directory
    Then JavaScript files should be accessible
    And CSS files should be accessible

  # ============================================================================
  # SCENARIO GROUP 2: Engine Status API
  # ============================================================================

  @api @engine
  Scenario: Get engine status returns valid response
    When I request GET "/api/engine/status"
    Then the response status should be 200
    And the response should be valid JSON
    And the engine status should contain "running" field
    And the engine status should contain "tradingEnabled" field
    And the engine status should contain "ringBufferCapacity" field

  @api @engine
  Scenario: Stop engine via API
    When I request POST "/api/engine/stop"
    Then the response status should be 200
    And the response should contain "stopped"

  @api @engine
  Scenario: Reset daily counters via API
    When I request POST "/api/engine/reset-daily"
    Then the response status should be 200
    And the response should contain "reset"

  @api @engine
  Scenario: Enable trading via API
    When I request POST "/api/engine/trading/enable"
    Then the response status should be 200
    And the response should indicate trading is enabled

  @api @engine
  Scenario: Disable trading via API
    When I request POST "/api/engine/trading/disable" with reason "Test disable"
    Then the response status should be 200
    And the response should indicate trading is disabled
    And the disable reason should be "Test disable"

  # ============================================================================
  # SCENARIO GROUP 3: Strategies API
  # ============================================================================

  @api @strategies
  Scenario: Get available strategy types
    When I request GET "/api/strategies/types"
    Then the response status should be 200
    And the response should be a list
    And the list should contain "Momentum"
    And the list should contain "Mean Reversion"

  @api @strategies
  Scenario: Create a new momentum strategy
    When I create a strategy with:
      | field      | value                                    |
      | type       | momentum                                 |
      | symbol     | AAPL                                     |
      | exchange   | ALPACA                                   |
      | parameters | {"shortPeriod":5,"longPeriod":10}       |
    Then the response status should be 200
    And the strategy should have an id

  @api @strategies
  Scenario: Get all strategies
    Given a strategy exists
    When I request GET "/api/strategies"
    Then the response status should be 200
    And the response should be a list of strategies

  @api @strategies
  Scenario: Stop a strategy
    Given a strategy exists
    When I stop the created strategy
    Then the response status should be 200

  @api @strategies
  Scenario: Delete a strategy
    Given a strategy exists
    When I delete the created strategy
    Then the response status should be 200

  # ============================================================================
  # SCENARIO GROUP 4: Positions API
  # ============================================================================

  @api @positions
  Scenario: Get all positions
    When I request GET "/api/positions"
    Then the response status should be 200
    And the response should be a list

  @api @positions
  Scenario: Get active positions only
    When I request GET "/api/positions/active"
    Then the response status should be 200
    And the response should be a list

  @api @positions
  Scenario: Get position summary with P&L
    When I request GET "/api/positions/summary"
    Then the response status should be 200
    And the summary should contain "totalPositions"
    And the summary should contain "realizedPnl"
    And the summary should contain "unrealizedPnl"

  # ============================================================================
  # SCENARIO GROUP 5: Orders API
  # ============================================================================

  @api @orders
  Scenario: Get all active orders
    When I request GET "/api/orders"
    Then the response status should be 200
    And the response should be a list

  @api @orders
  Scenario: Create a new order
    When I create an order with:
      | field    | value  |
      | symbol   | AAPL   |
      | exchange | ALPACA |
      | side     | BUY    |
      | type     | LIMIT  |
      | quantity | 100    |
      | price    | 15000  |
    Then the response status should be 200
    And the order should have a client order id

  @api @orders
  Scenario: Cancel an order via API
    Given an active order exists
    When I cancel the order via API
    Then the response status should be 200

  # ============================================================================
  # SCENARIO GROUP 6: Error Handling
  # ============================================================================

  @api @errors
  Scenario: Non-existent endpoint returns error status
    When I request GET "/api/nonexistent"
    Then the response status should be an error

  @api @errors
  Scenario: Invalid strategy type returns error
    When I create a strategy with invalid type "InvalidType"
    Then the response status should be an error

  # ============================================================================
  # SCENARIO GROUP 7: CORS Support
  # ============================================================================

  @api @cors
  Scenario: API supports CORS requests
    When I make a CORS preflight request to "/api/engine/status"
    Then the response should include CORS headers
