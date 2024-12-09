package org.example;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Stream;

public class SimpleMovingAverage {
    private static final double INITIAL_CAPITAL = 1_000_000.0;

    private static BigDecimal calculateMovingAverage(List<StockData> stockData, int endIndex, int window) {
        return stockData.stream()
                .map(StockData::getAdjClose)
                .skip(endIndex-window)
                .limit(window)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window), MathContext.DECIMAL128);
    }

    public static double calculateMaxDrawdown(List<Double> portfolioValues) {
        double maxDrawdown = 0.0;
        double peak = portfolioValues.get(0);

        for (int i = 1; i < portfolioValues.size(); i++) {
            peak = Math.max(peak, portfolioValues.get(i));
            double currentDrawdown = (peak - portfolioValues.get(i)) / peak;
            maxDrawdown = Math.max(maxDrawdown, currentDrawdown);
        }

        return maxDrawdown;
    }

    private static void simulate(int shortWindow, int longWindow) {
        StockDataManager dataManager = new StockDataManager();
        dataManager.loadHistoricalDataFromCSV("stock_data/consolidated_stock_data.csv");
        List<String> Stocks = dataManager.getStocks();

        Map<String, Long> Portfolio = new HashMap<>();
        Map<String, BigDecimal> Capital = new HashMap<>();
        Map<String, BigDecimal> ClosingPrice = new HashMap<>();
        List<BigDecimal> dailyReturns = new ArrayList<>(Stream.generate(() -> BigDecimal.ZERO)
                .limit(dataManager.getHistoricalData(Stocks.get(0)).size()-longWindow)
                .toList());

        for(String stock: Stocks) {
            BigDecimal capital = BigDecimal.valueOf(INITIAL_CAPITAL/Stocks.size());

            Portfolio.put(stock, 0L);
            List<StockData> stockData = dataManager.getHistoricalData(stock);

            for(int i=longWindow; i<stockData.size(); i++) {
                BigDecimal shortAverage = calculateMovingAverage(stockData, i, shortWindow);
                BigDecimal longAverage = calculateMovingAverage(stockData, i, longWindow);
                BigDecimal closingPrice = stockData.get(i).getAdjClose();

                if(shortAverage.compareTo(longAverage) > 0) {
                    long portfolio = Portfolio.get(stock);
                    long bought = Math.min(capital.divideToIntegralValue(closingPrice).longValue(), stockData.get(i).getVolume());

                    Portfolio.put(stock, portfolio+bought);
                    capital = capital.subtract(closingPrice.multiply(new BigDecimal(bought)));
                } else if(shortAverage.compareTo(longAverage) < 0) {
                    long portfolio = Portfolio.get(stock);
                    long sold = Math.min(portfolio, stockData.get(i).getVolume());

                    Portfolio.put(stock, portfolio-sold);
                    capital = capital.add(closingPrice.multiply(new BigDecimal(sold)));
                }

                BigDecimal newportfolioValue = capital.add(stockData.get(i).getAdjClose().multiply(BigDecimal.valueOf(Portfolio.get(stock))));
                ClosingPrice.put(stock, closingPrice);
                dailyReturns.set(i-longWindow, dailyReturns.get(i-longWindow).add(newportfolioValue));
            }

            Capital.put(stock, capital);
        }

        double maxDrawdown = calculateMaxDrawdown(dailyReturns.stream().map(BigDecimal::doubleValue).toList());

        for(int i=dailyReturns.size()-1; i>0; i--) {
            dailyReturns.set(i, dailyReturns.get(i).subtract(dailyReturns.get(i-1)).divide(dailyReturns.get(i-1), MathContext.DECIMAL128));
        }
        dailyReturns.set(0, BigDecimal.ZERO);

        BigDecimal finalCapital = new BigDecimal(0);
        for(Map.Entry<String, BigDecimal> entry : Capital.entrySet()) {
            finalCapital = finalCapital.add(entry.getValue());
            finalCapital = finalCapital.add(BigDecimal.valueOf(Portfolio.get(entry.getKey())).multiply(ClosingPrice.get(entry.getKey())));
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

        // Output Results
        System.out.printf("Backtest Results using shortWindow = %d and longWindow = %d%n", shortWindow, longWindow);
        System.out.printf("Initial Capital: $%.6f%n", INITIAL_CAPITAL);
        System.out.printf("Final Capital: $%.6f%n", finalCapital);
        System.out.printf("Linear Regression coefficients: %.6f, %.6f%n", coefficients[0], coefficients[1]);
        System.out.printf("Accuracy of trading signal: %.6f%n", dailyReturns.stream()
                .map(r -> r.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128)
                .doubleValue());
        System.out.printf("Maximum Drawdown : %.6f%n", maxDrawdown);
        System.out.printf("Annualized Sharpe Ratio: %.6f%n%n", sharpeRatio.doubleValue()*Math.sqrt(252));
    }

    public static void main(String[] args) {
        simulate(10, 50);
    }
}