package org.example;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class EMAReversalStrategy {
    private static final double INITIAL_CAPITAL = 1_000_000.0;

    private static BigDecimal calculateEMA(List<BigDecimal> prices, int period, int endIndex) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1.0));
        BigDecimal ema = prices.get(endIndex - period);

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            ema = prices.get(i).subtract(ema)
                    .multiply(multiplier)
                    .add(ema, MathContext.DECIMAL128);
        }

        return ema;
    }

    private static BigDecimal calculateSMA(List<BigDecimal> values, int endIndex, int window) {
        return values.stream()
                .skip(endIndex - window + 1)
                .limit(window)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window), MathContext.DECIMAL128);
    }

    private static boolean isAscendingOrder(List<BigDecimal> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i - 1).compareTo(values.get(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDescendingOrder(List<BigDecimal> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i - 1).compareTo(values.get(i)) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static double calculateSharpeRatio(List<BigDecimal> portfolioValues) {
        // Calculate daily returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < portfolioValues.size(); i++) {
            double dailyReturn = portfolioValues.get(i).subtract(portfolioValues.get(i - 1)).doubleValue()
                    / portfolioValues.get(i - 1).doubleValue();
            returns.add(dailyReturn);
        }

        // Calculate mean and standard deviation of returns
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - meanReturn, 2))
                .average()
                .orElse(0);
        double stdDeviation = Math.sqrt(variance);

        // Sharpe ratio = mean return / standard deviation
        return meanReturn / stdDeviation;
    }

    private static double calculateMaxDrawdown(List<BigDecimal> portfolioValues) {
        double maxDrawdown = 0;
        double peakValue = portfolioValues.get(0).doubleValue();
        
        for (BigDecimal value : portfolioValues) {
            double currentValue = value.doubleValue();
            if (currentValue > peakValue) {
                peakValue = currentValue; // Update peak value
            }
            double drawdown = (peakValue - currentValue) / peakValue;
            maxDrawdown = Math.max(maxDrawdown, drawdown);
        }
        
        return maxDrawdown;
    }

    public static void simulate(int shortPeriod, int longPeriod) {
        StockDataManager dataManager = new StockDataManager();
        dataManager.loadHistoricalDataFromCSV("stock_data/consolidated_stock_data.csv");
        List<String> stocks = dataManager.getStocks();

        for (String stock : stocks) {
            List<StockData> stockData = dataManager.getHistoricalData(stock);
            List<BigDecimal> closePrices = new ArrayList<>();
            List<BigDecimal> volumes = new ArrayList<>();
            stockData.forEach(sd -> {
                closePrices.add(sd.getAdjClose());
                volumes.add(BigDecimal.valueOf(sd.getVolume()));
            });

            BigDecimal capital = BigDecimal.valueOf(INITIAL_CAPITAL / stocks.size());
            int totalTrades = 0;
            List<BigDecimal> portfolioValues = new ArrayList<>();
            portfolioValues.add(capital);  // Add initial capital to the portfolio values

            for (int i = longPeriod; i < stockData.size(); i++) {
                List<BigDecimal> emas = new ArrayList<>();
                int[] emaPeriods = {shortPeriod, shortPeriod + 5, shortPeriod + 10, shortPeriod + 15, longPeriod};
                for (int period : emaPeriods) {
                    emas.add(calculateEMA(closePrices, period, i));
                }

                StockData currentData = stockData.get(i);
                BigDecimal currentClose = currentData.getAdjClose();
                BigDecimal currentOpen = currentData.getOpen();
                BigDecimal currentVolume = BigDecimal.valueOf(currentData.getVolume());
                BigDecimal smaVolume = calculateSMA(volumes, i, 10);

                // Scenario 1: Uptrend
                if (isAscendingOrder(emas) &&
                        currentClose.compareTo(emas.get(0)) > 0 &&
                        currentOpen.compareTo(emas.get(emas.size() - 1)) < 0 &&
                        currentVolume.compareTo(smaVolume) > 0) {
                    System.out.printf("Buy Signal (Scenario 1) for %s on %s%n", stock, currentData.getDate());
                    totalTrades++;
                    capital = capital.multiply(BigDecimal.valueOf(1.01));  // Assuming 1% return for simplicity
                    portfolioValues.add(capital);
                }

                // Scenario 2: Downtrend
                if (isDescendingOrder(emas) &&
                        currentClose.compareTo(emas.get(emas.size() - 1)) > 0 &&
                        currentOpen.compareTo(emas.get(0)) < 0 &&
                        currentVolume.compareTo(smaVolume) > 0) {
                    System.out.printf("Buy Signal (Scenario 2) for %s on %s%n", stock, currentData.getDate());
                    totalTrades++;
                    capital = capital.multiply(BigDecimal.valueOf(0.99));  // Assuming -1% return for simplicity
                    portfolioValues.add(capital);
                }
            }

            // Calculate performance metrics
            double sharpeRatio = calculateSharpeRatio(portfolioValues);
            double maxDrawdown = calculateMaxDrawdown(portfolioValues);

            System.out.printf("Total trades executed for %s: %d%n", stock, totalTrades);
            System.out.printf("Sharpe Ratio for %s: %.2f%n", stock, sharpeRatio);
            System.out.printf("Max Drawdown for %s: %.2f%%%n", stock, maxDrawdown * 100);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Get short and long period values from user
        System.out.print("Enter short EMA period: ");
        int shortPeriod = scanner.nextInt();

        System.out.print("Enter long EMA period: ");
        int longPeriod = scanner.nextInt();

        // Simulate with user-defined periods
        simulate(shortPeriod, longPeriod);
    }
}
