package org.example;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

import static org.example.SimpleMovingAverage.calculateMaxDrawdown;

public class ExponentialMovingAverage {

    private static final double INITIAL_CAPITAL = 1_000_000.0;
    private static final double RISK_PER_TRADE = 0.005;

    private static BigDecimal calculateEMA(List<StockData> stockData, int endIndex, int window) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (window + 1));

        BigDecimal ema = stockData.stream()
                .map(StockData::getAdjClose)
                .skip(endIndex - window)
                .limit(window)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window), MathContext.DECIMAL128);

        for (int i = endIndex - window + 1; i <= endIndex; i++) {
            BigDecimal price = stockData.get(i).getAdjClose();
            ema = price.multiply(multiplier).add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }

        return ema;
    }

    private static BigDecimal calculateRSI(List<StockData> stockData, int endIndex, int period) {
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            BigDecimal change = stockData.get(i).getAdjClose().subtract(stockData.get(i - 1).getAdjClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }

        if (lossSum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100); // Max RSI if no losses
        }

        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);

        BigDecimal rs = avgGain.divide(avgLoss, MathContext.DECIMAL128);
        return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(rs.add(BigDecimal.ONE), MathContext.DECIMAL128));
    }

    private static BigDecimal calculateATR(List<StockData> stockData, int endIndex, int period) {
        BigDecimal atrSum = BigDecimal.ZERO;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            BigDecimal highLow = stockData.get(i).getHigh().subtract(stockData.get(i).getLow());
            BigDecimal highPrevClose = stockData.get(i).getHigh().subtract(stockData.get(i - 1).getAdjClose()).abs();
            BigDecimal lowPrevClose = stockData.get(i).getLow().subtract(stockData.get(i - 1).getAdjClose()).abs();

            BigDecimal trueRange = highLow.max(highPrevClose).max(lowPrevClose);
            atrSum = atrSum.add(trueRange);
        }

        return atrSum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);
    }

    private static void simulate(int shortWindow, int longWindow) {
        StockDataManager dataManager = new StockDataManager();
        dataManager.loadHistoricalDataFromCSV("stock_data/consolidated_stock_data.csv");
        List<String> stocks = dataManager.getStocks();

        BigDecimal cash = BigDecimal.valueOf(INITIAL_CAPITAL);
        Map<String, Long> portfolio = new HashMap<>();
        Map<String, BigDecimal> closingPrice = new HashMap<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();
        List<BigDecimal> portfolioValues = new ArrayList<>();
        BigDecimal portfolioValue = cash;

        for (int i = longWindow; i < dataManager.getHistoricalData(stocks.get(0)).size(); i++) {
            Map<String, Integer> signals = new HashMap<>();

            // Generate signals with RSI and EMA-based conditions
            for (String stock : stocks) {
                List<StockData> stockData = dataManager.getHistoricalData(stock);
                closingPrice.put(stock, stockData.get(i).getAdjClose());

                BigDecimal shortEMA = calculateEMA(stockData, i, shortWindow);
                BigDecimal longEMA = calculateEMA(stockData, i, longWindow);

                BigDecimal rsi = calculateRSI(stockData, i, 14); // 14-day RSI

                if (shortEMA.compareTo(longEMA) > 0 && rsi.compareTo(BigDecimal.valueOf(30)) > 0) {
                    signals.put(stock, 1); // Buy signal
                } else if (shortEMA.compareTo(longEMA) < 0 && rsi.compareTo(BigDecimal.valueOf(70)) < 0) {
                    signals.put(stock, -1); // Sell signal
                } else {
                    signals.put(stock, 0); // Hold
                }
            }

            // Adjust positions with volatility-based sizing
            for (String stock : stocks) {
                BigDecimal atr = calculateATR(dataManager.getHistoricalData(stock), i, 14);
                BigDecimal price = closingPrice.get(stock);

                BigDecimal riskAmount = portfolioValue.multiply(BigDecimal.valueOf(RISK_PER_TRADE));
                BigDecimal stopLossDistance = atr.multiply(BigDecimal.valueOf(3));

                long maxSharesBasedOnRisk = riskAmount
                        .divide(stopLossDistance, MathContext.DECIMAL128)
                        .divide(price, MathContext.DECIMAL128)
                        .longValue();

                if (signals.get(stock) == 1) {
                    long affordableShares = cash.divideToIntegralValue(price).longValue();
                    long sharesToBuy = Math.min(maxSharesBasedOnRisk, affordableShares);

                    if (sharesToBuy > 0) {
                        cash = cash.subtract(price.multiply(BigDecimal.valueOf(sharesToBuy)));
                        portfolio.put(stock, portfolio.getOrDefault(stock, 0L) + sharesToBuy);
                    }
                } else if (signals.get(stock) == -1) {
                    long currentHoldings = portfolio.getOrDefault(stock, 0L);
                    cash = cash.add(price.multiply(BigDecimal.valueOf(maxSharesBasedOnRisk)));
                    portfolio.put(stock, currentHoldings - maxSharesBasedOnRisk);
                }
            }

            BigDecimal newPortfolioValue = cash;
            for (String stock : stocks) {
                BigDecimal currentHolding = BigDecimal.valueOf(portfolio.getOrDefault(stock, 0L));
                newPortfolioValue = newPortfolioValue.add(currentHolding.multiply(closingPrice.get(stock)));
            }

            BigDecimal dailyReturn = newPortfolioValue.subtract(portfolioValue).divide(portfolioValue, MathContext.DECIMAL128);
            dailyReturns.add(dailyReturn);
            portfolioValue = newPortfolioValue;
            portfolioValues.add(portfolioValue);
        }

        // Calculate Sharpe ratio
        BigDecimal averageReturn = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128);
        BigDecimal variance = dailyReturns.stream()
                .map(r -> r.subtract(averageReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128);
        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal sharpeRatio = averageReturn.divide(standardDeviation, MathContext.DECIMAL128);

        // Linear regression on market returns
        double[] coefficients = dataManager.performRegression(dailyReturns, longWindow);

        System.out.printf("Backtest Results using shortWindow = %d and longWindow = %d%n", shortWindow, longWindow);
        System.out.printf("Initial Capital: $%.6f%n", INITIAL_CAPITAL);
        System.out.printf("Final Capital: $%.6f%n", portfolioValue);
        System.out.printf("Linear Regression coefficients: %.6f, %.6f%n", coefficients[0], coefficients[1]);
        System.out.printf("Accuracy of trading signal: %.6f%n", dailyReturns.stream()
                .map(r -> r.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128)
                .doubleValue());
        System.out.printf("Maximum Drawdown : %.6f%n", calculateMaxDrawdown(portfolioValues.stream().map(BigDecimal::doubleValue).toList()));
        System.out.printf("Annualized Sharpe Ratio: %.6f%n%n", sharpeRatio.doubleValue()*Math.sqrt(252));
    }

    public static void main(String[] args) {
        simulate(10, 50);
    }
}