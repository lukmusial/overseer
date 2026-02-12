Feature: Advanced Trading Strategies
  As a trader
  I want to use advanced trading strategies
  So that I can profit from different market conditions

  @strategy
  Scenario: Create an EMA+ADX+RSI strategy
    When I create an ema_adx_rsi strategy for "BTCUSDT" on "BINANCE" with parameters:
      | parameter        | value |
      | fastEmaPeriod    | 9     |
      | slowEmaPeriod    | 21    |
      | adxPeriod        | 14    |
      | adxThreshold     | 25    |
      | rsiPeriod        | 14    |
      | rsiBullThreshold | 55    |
      | rsiBearThreshold | 45    |
      | maxPositionSize  | 100   |
    Then the advanced strategy should be created successfully
    And the advanced strategy state should be "INITIALIZED"

  @strategy
  Scenario: EMA+ADX+RSI generates buy signal in uptrend with ADX>25 and RSI>55
    Given I have a running ema_adx_rsi strategy for "BTCUSDT" on "BINANCE"
    When I feed a strong uptrend of 50 quotes
    Then the fast EMA should be above the slow EMA
    And the RSI should be above 50

  @strategy
  Scenario: EMA+ADX+RSI generates no signal when ADX is low
    Given I have a running ema_adx_rsi strategy for "BTCUSDT" on "BINANCE"
    When I feed 50 sideways quotes around 50000
    Then the signal should be zero or near-zero

  @strategy
  Scenario: Create a Bollinger Squeeze strategy
    When I create a bollinger_squeeze strategy for "ETHUSDT" on "BINANCE" with parameters:
      | parameter     | value |
      | bbPeriod      | 20    |
      | bbStdDev      | 2.5   |
      | kcPeriod      | 20    |
      | kcAtrPeriod   | 14    |
      | kcMultiplier  | 2.0   |
      | macdFast      | 8     |
      | macdSlow      | 17    |
      | macdSignal    | 9     |
      | maxPositionSize | 100 |
    Then the advanced strategy should be created successfully
    And the advanced strategy state should be "INITIALIZED"

  @strategy
  Scenario: Bollinger Squeeze detects squeeze and generates signal on breakout
    Given I have a running bollinger_squeeze strategy for "ETHUSDT" on "BINANCE"
    When I feed 25 stable quotes to create a squeeze
    And I feed 10 breakout quotes trending upward
    Then the MACD histogram should be positive

  @strategy
  Scenario: Create a VWAP Mean Reversion strategy
    When I create a vwap_mean_reversion strategy for "BTCUSDT" on "BINANCE" with parameters:
      | parameter              | value |
      | upperSigma             | 2.3   |
      | lowerSigma             | 2.3   |
      | exitSigma              | 0.5   |
      | maxHoldMinutes         | 240   |
      | volumeFilterMultiplier | 0.0   |
      | maxPositionSize        | 100   |
    Then the advanced strategy should be created successfully
    And the advanced strategy state should be "INITIALIZED"

  @strategy
  Scenario: VWAP Mean Reversion generates buy signal below lower band
    Given I have a running vwap_mean_reversion strategy for "BTCUSDT" on "BINANCE"
    When I feed 50 quotes around 50000 to build VWAP
    And the price drops far below VWAP
    Then the vwap strategy should generate a buy signal

  @strategy
  Scenario: VWAP Mean Reversion exits when price returns to VWAP
    Given I have a running vwap_mean_reversion strategy for "BTCUSDT" on "BINANCE"
    And I feed 15 quotes around 50000 to build VWAP
    And the strategy has a long position of 50 units
    When the price returns to VWAP
    Then the signal should indicate exit
