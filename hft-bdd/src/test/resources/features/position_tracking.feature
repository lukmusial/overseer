Feature: Position Tracking
  As a trader
  I want to track my positions accurately
  So that I can monitor P&L and risk

  Scenario: Opening a long position
    Given I have a long position of 0 shares at 0.00
    When I buy 100 shares at price 150.00
    Then my position should be long
    And my position quantity should be 100
    And my average entry price should be 150.00

  Scenario: Adding to a long position
    Given I have a long position of 100 shares at 150.00
    When I buy 50 more shares at price 152.00
    Then my position quantity should be 150
    And my average entry price should be approximately 150.67

  Scenario: Reducing a long position with profit
    Given I have a long position of 100 shares at 150.00
    When I sell 50 shares at price 155.00
    Then my position quantity should be 50
    And my realized P&L should be positive

  Scenario: Closing a long position
    Given I have a long position of 100 shares at 150.00
    When I sell 100 shares at price 155.00
    Then my position should be flat
    And my realized P&L should be 500.00

  Scenario: Opening a short position
    Given I have a short position of 0 shares at 0.00
    When I sell 100 shares at price 150.00
    Then my position should be short
    And my position quantity should be -100

  Scenario: Reversing from long to short
    Given I have a long position of 100 shares at 150.00
    When I sell 150 shares at price 152.00
    Then my position should be short
    And my position quantity should be -50
    And my realized P&L should be positive

  Scenario: Tracking unrealized P&L
    Given I have a long position of 100 shares at 150.00
    When the market price updates to 155.00
    Then my unrealized P&L should be 500.00

  Scenario: Tracking maximum drawdown
    Given I have a long position of 100 shares at 150.00
    When the market price drops to 145.00
    Then my maximum drawdown should be recorded
