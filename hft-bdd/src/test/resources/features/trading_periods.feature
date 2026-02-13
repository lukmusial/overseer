Feature: Trading Periods
  As a trader operating in UK business hours
  I want the system to detect trading periods
  So that position sizing and strategy selection are optimized

  @strategy
  Scenario Outline: Detect correct trading period for UTC time
    When the UTC time is "<time>"
    Then the trading period should be "<period>"
    And the position multiplier should be <multiplier>

    Examples:
      | time  | period       | multiplier |
      | 08:00 | LONDON_OPEN  | 0.75       |
      | 08:30 | LONDON_OPEN  | 0.75       |
      | 09:00 | EU_MORNING   | 0.75       |
      | 10:30 | EU_MORNING   | 0.75       |
      | 11:00 | PRE_OVERLAP  | 0.50       |
      | 11:45 | PRE_OVERLAP  | 0.50       |
      | 12:00 | OVERLAP      | 1.00       |
      | 14:00 | OVERLAP      | 1.00       |
      | 16:00 | POST_EU      | 0.50       |
      | 17:30 | POST_EU      | 0.50       |
      | 18:00 | OFF_HOURS    | 0.25       |
      | 22:00 | OFF_HOURS    | 0.25       |
      | 03:00 | OFF_HOURS    | 0.25       |

  @strategy
  Scenario: Recommended strategies for OVERLAP period
    When the UTC time is "14:00"
    Then the trading period should be "OVERLAP"
    And the recommended strategies should include "ema_adx_rsi"
    And the recommended strategies should include "bollinger_squeeze"

  @strategy
  Scenario: Recommended strategies for EU_MORNING period
    When the UTC time is "09:30"
    Then the trading period should be "EU_MORNING"
    And the recommended strategies should include "ema_adx_rsi"
    And the recommended strategies should include "vwap_mean_reversion"

  @strategy
  Scenario: Recommended strategies for POST_EU period
    When the UTC time is "17:00"
    Then the trading period should be "POST_EU"
    And the recommended strategies should include "vwap_mean_reversion"

  @strategy
  Scenario: OFF_HOURS has no recommended strategies
    When the UTC time is "22:00"
    Then the trading period should be "OFF_HOURS"
    And there should be no recommended strategies
