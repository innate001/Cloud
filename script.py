import yfinance as yf
import pandas as pd
import os
from datetime import datetime, timedelta

def download_stock_data(tickers):
    # Create a directory to store stock data if it doesn't exist
    os.makedirs('stock_data', exist_ok=True)
    
    # Calculate date range (5 years from today)
    end_date = datetime.now()
    start_date = end_date - timedelta(days=5*365)
    
    # List to collect DataFrames for each stock
    all_stocks_data = []
    
    # Download data for each ticker
    for ticker in tickers:
        try:
            # Download historical market data
            stock_data = yf.download(ticker, start=start_date, end=end_date)
            
            # Check if data is not empty
            if not stock_data.empty:
                # Rename columns to avoid issues with column naming
                stock_data.columns = [
                    'Open', 'High', 'Low', 'Close', 'Adj Close', 'Volume'
                ]
                
                # Reset index to make Date a column
                stock_data = stock_data.reset_index()
                
                # Add a column to identify the stock
                stock_data['Ticker'] = ticker
                
                # Append to the list of DataFrames
                all_stocks_data.append(stock_data)
                
                print(f"Successfully downloaded data for {ticker}")
            else:
                print(f"No data available for {ticker}")
        
        except Exception as e:
            print(f"Error downloading data for {ticker}: {e}")
    
    # Combine all stock data into a single DataFrame
    if all_stocks_data:
        consolidated_data = pd.concat(all_stocks_data, ignore_index=True)
        
        # Save to a single CSV file
        output_file = 'stock_data/consolidated_stock_data.csv'
        consolidated_data.to_csv(output_file, index=False)
        print(f"\nConsolidated data saved to {output_file}")
        
        # Print summary information
        print("\nData Summary:")
        summary = consolidated_data.groupby('Ticker').agg({
            'Date': ['min', 'max'],
            'Close': ['count', 'mean', 'min', 'max']
        })
        print(summary)
        
        return consolidated_data
    else:
        print("No stock data was downloaded.")
        return None

# List of stock tickers
tickers = [
    "MSFT", "AAPL", "GOOGL", "AMZN", "NVDA", 
    "META", "TSLA", "JPM", "V", "UNH", 
    "MA", "AVGO", "ADBE", "CRM", "NFLX", 
    "INTC", "CSCO", "PYPL", "ORCL", "ASML"
]

# Run the download function
download_stock_data(tickers)