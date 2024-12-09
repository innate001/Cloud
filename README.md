# Backtesting Engine

## How to run code
1. Run: pip install yfinance <br>
   Then => python script.py

2. Run:
    mvn clean <br>
    mvn install <br>
    java -jar target/simple-moving-average.jar //to run SMA <br>
    java -jar target/exponential-moving-average.jar //to run EMA <br>
    java -jar target/ema-reversal-strategy.jar      // to run EMAreversal<br>


## Methodology
I have tried to implement 3 Strategies <br>
 a). SimpleMovingAverage <br> 
 b). Exponential Moving Average <br>
 c). Multi EMA strategy to catch hold of periods when trends reverse <br>

 Below is the explanantion of the three in order:

 ### Simple Moving Average

1. Divide the initial capital **equally** among all the stocks. 
2. Then for each stock, we maintain two averages: short average (over 10 days) and long average (over 50 days).
3. We consider a **long-only strategy**, where we can only sell stocks as much as we own.
4. Whenever the short average exceeds the long average, it is a signal to buy and when the short average falls below the long average, it is a signal to sell.

### Exponential Moving Average
Building on the above strategy, certain improvements are introduced:
1. **Exponential moving average (EMA)** is used instead of the simple moving average (SMA) to give more weight to recent prices and less weight to older prices
2. Buy/sell signals for stock are now generated using the previous condition for long and short averages along with **RSI** over the last 14 days
   - **Buy** a stock only if **short EMA > long EMA** and **RSI < 70** (RSI > 70 indicates that the stock is overbought)
   - **Sell** a stock only if **short EMA < long EMA** and **RSI > 30** (RSI < 30 indicates that the stock is oversold)
3. Now we define `RISK_PER_TRADE` as **0.5%**, which is the maximum value of the portfolio that we can invest per trade.
4. To calculate the position size of a trade, we use **ATR** (Average True Range) calculated over the last 14 days. This is to limit trading in a stock that is highly volatile.
5. The position size for a stock is calculated as `RISK_PER_TRADE * portfolioValue / StopLossDistance`. Stop loss distance is the maximum loss per share that we can bear. It is defined to be thrice the ATR. 
6. Short selling of stocks can take place since the position size is now controlled.

We observe that the Sharpe Ratio improves from the first strategy to the second strategy.

# Multi-EMAs-Strategy-for-Trend-Reversal-and-Continuation
"Multi-EMAs Strategy for Trend Reversal and Continuation"

-	Hypothesis
-	Examples
-	Stock selection and target
-	Implementation
-	sample run
-	Visualizing the results
-	Conclusion
-	suggestions


Hypothesis:
My strategy is based on the hypothesis that the simultaneous use of multiple exponential moving averages (EMAs) of varying periods along with the closing and opening price of the stocks can effectively identify potential trend reversals and continuations in financial markets. The hypothesis assumes that when these EMAs are in a specific configuration (either in the uptrend or downtrend), they can provide insights into the underlying market sentiment and price momentum.

EMAs period used: 20,25,30,35,40,45,50,55

The strategy seeks two key scenarios:

Scenario 1:

When all the EMAs are in an uptrend i.e. EMA20>EMA25>EMA30>EMA35>EMA40>EMA45>EMA50>EMA55 enter in the long trade
when the close price of the candle is above the EMA20 and open is below EMA55.

Scenario 2:

When all the EMAs are in a downtrend i.e. EMA20<EMA25<EMA30<EMA35<EMA40<EMA45<EMA50<EMA55 enter in the long trade
when the close price of the candle is above the EMA55 and open is below EMA20.

The strategy also incorporates volume on the entry candle should be greater than the average of the past 10 candles' volume


Example of how trade initiates:

•	First check if all the EMAs are either in ascending or descending order.
•	Considering in the ascending order, if the current closing price is above the 20-period EMA.
•	Check for the opening price below the 20-period EMA.
 
•	If these conditions are satisfied then check for the volume to ensure that the current volume is greater than the 10-period SMA of volume.
•	If all conditions are met, a buy order is placed.

Few examples based on the different timeframes:


AXIS BANK hourly timeframe chart and a significant move of 8.42% after the buy signal candle(Trend continuation)

![image](https://github.com/user-attachments/assets/fe82eb6b-caaa-4fd2-b8d4-57c4e4c8de48)

 
PFC hourly time frame chart and a significant move of 17% after the buy signal candle ( Trend reversal)
![image](https://github.com/user-attachments/assets/46f47faa-f732-4104-b324-2ebf2c1863f2)


So, after seeing the strategy robustness in the different timeframes, I am using an hourly timeframe chart for backtesting the strategy This strategy works on any timeframe chart without any exception just need to change the target and stop loss depending on the timeframe.


Target and stop loss
The strategy uses a stop loss set at 5% below the buy price and a target sell price set at 5% above the buy price.
More sophisticated approaches could also involve the concept of trailing stop loss


Stock selection criteria:
This strategy works with its full potential on highly liquid stocks actively traded on the exchange. So, selecting some of the most liquid stocks on the NSE based on market cap
1. JINDAL STEEL
2. TATA MOTORS	
3.	WIPRO
4.	AXIS BANK
5.	ITC


Implementation of the strategy:
Coding the strategy in python using the backtrader library CODE- Code


Data source- using the YAHOO FINANCE stock Data by importing its Library.
Backtesting period: 1st January, 2000 to 1st April  2024.



Some assumptions during the backtest:
•	My strategy first checks if there's an existing order; if there is, it does not execute any new orders.
•	I am not taking leverage in trades as some of the trades take more than a day, so considering Indian market trades aren’t eligible for leverage

Result format:
•	Result shows the date when buy executed and sell executed including CMP at that time
•	Shows the number of trades closed, number of win trades, number of losing trades, number of open trades, and strike rate (Win ratio).


After combining the overall result for 5 stocks:


Conclusion:
•	This trading strategy showcases a systematic approach to capturing potential trends and reversals in financial markets, relying on the power of EMAs, price analysis, and volume confirmation.
•	This strategy has great potential not only in the Indian financial markets But also works with no limitation in the other global markets as it is purely based on the opening and closing data.
•	A high degree of market liquidity is essential for the strategy to be successful.
Recommendations:
•	This is only a long strategy one can convert this into a long short strategy
•	The concept of the trailing stop loss can be used to maximize the profit


