package org.example;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class StockDataManager {
    private static final LocalDate START_DATE = LocalDate.of(2020, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2024, 11, 30);

    private final Map<String, List<StockData>> historicalData = new HashMap<>();

    public void loadHistoricalDataFromCSV(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Read and skip header
            String headerLine = br.readLine();

            // Validate header
            String[] headers = headerLine.split(",");
            if (headers.length < 8 || !headers[7].trim().equalsIgnoreCase("Ticker")) {
                throw new IllegalArgumentException("Invalid CSV format. Expected 8 columns with Ticker as last column.");
            }

            // Prepare a map to collect data for each stock
            Map<String, List<StockData>> stockDataMap = new HashMap<>();

            String line;
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");

                // Ensure we have enough columns
                if (columns.length < 8) continue;

                try {
                    // Parse CSV columns
                    LocalDate date = LocalDate.parse(columns[0]);
                    String ticker = columns[7].trim(); // Last column is Ticker
                    StockData stockData = getData(columns, date, ticker);

                    // Add to stock-specific list
                    stockDataMap.computeIfAbsent(ticker, k -> new ArrayList<>()).add(stockData);

                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Error parsing line: " + line);
                }
            }

            // Filter and store only the stocks in our STOCKS list
            for (String stock : stockDataMap.keySet()) {
                List<StockData> stockData = stockDataMap.get(stock);
                if (stockData != null) {
                    stockData.sort(Comparator.comparing(StockData::getDate));

                    stockData = stockData.stream()
                            .filter(sd -> !sd.getDate().isBefore(START_DATE) && !sd.getDate().isAfter(END_DATE))
                            .collect(Collectors.toList());

                    historicalData.put(stock, stockData);
                }
            }

            System.out.println("Loaded historical data for " + historicalData.size() + " stocks");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static StockData getData(String[] columns, LocalDate date, String ticker) {
        BigDecimal open = new BigDecimal(columns[1]);
        BigDecimal high = new BigDecimal(columns[2]);
        BigDecimal low = new BigDecimal(columns[3]);
        BigDecimal close = new BigDecimal(columns[4]);
        BigDecimal adjClose = new BigDecimal(columns[5]);
        long volume = Long.parseLong(columns[6]);

        // Create StockData object
        return new StockData(date,open,high,low,close,adjClose,volume, ticker);
    }

    public List<StockData> getHistoricalData(String symbol) {
        return historicalData.get(symbol);
    }

    public List<String> getStocks() {
        return new ArrayList<>(historicalData.keySet());
    }

    public List<BigDecimal> calculateEqualWeightedMarketReturns() {
        List<BigDecimal> marketReturns = new ArrayList<>();
        marketReturns.add(BigDecimal.ZERO);
        List<String> stocks = getStocks();
        int dataLength = getHistoricalData(stocks.get(0)).size();

        for (int i = 1; i < dataLength; i++) {
            BigDecimal totalReturn = BigDecimal.ZERO;

            for (String stock : stocks) {
                List<StockData> stockData = getHistoricalData(stock);
                BigDecimal previousClose = stockData.get(i-1).getAdjClose();
                BigDecimal currentClose = stockData.get(i).getAdjClose();

                BigDecimal stockReturn = currentClose.subtract(previousClose)
                        .divide(previousClose, MathContext.DECIMAL128);

                totalReturn = totalReturn.add(stockReturn);
            }

            BigDecimal averageMarketReturn = totalReturn.divide(
                    BigDecimal.valueOf(stocks.size()),
                    MathContext.DECIMAL128
            );

            marketReturns.add(averageMarketReturn);
        }

        return marketReturns;
    }

    public double[] performRegression(List<BigDecimal> strategyReturns, int window) {
        List<BigDecimal> marketReturns = calculateEqualWeightedMarketReturns();
        int n = marketReturns.size()-window;

        // Calculate means
        double marketMean = marketReturns.stream()
                .skip(window)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        double strategyMean = strategyReturns.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        // Calculate covariance and variance
        double covariance = 0;
        double marketVariance = 0;

        for (int i = 0; i < n; i++) {
            covariance += (marketReturns.get(i).doubleValue() - marketMean)
                    * (strategyReturns.get(i).doubleValue() - strategyMean);
            marketVariance += Math.pow(marketReturns.get(i).doubleValue() - marketMean, 2);
        }

        covariance /= (n - 1);
        marketVariance /= (n - 1);

        double beta = covariance / marketVariance;
        double alpha = strategyMean - (beta * marketMean);

        return new double[]{alpha, beta};
    }
}