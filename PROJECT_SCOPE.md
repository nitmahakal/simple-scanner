# Locked V1 scope

1. Fresh standalone repository. No code from the old NSE Research App.
2. Simple Android UI, not a giant all-in-one screen.
3. Scanner engine based on the supplied Colab logic.
4. Indicators: Close, EMA, HMA, RSI, EMA of RSI, MACD/Signal/Histogram, Stoch RSI K/D, Numeric Value, Reverse RSI, Reverse Stoch RSI, Reverse Stoch RSI %K/%D.
5. Reverse Stoch RSI inputs remain user supplied.
6. Comparators: Above, Below, Equal, Cross Above, Cross Below, Near By, May Go To Cross Above, May Go To Cross Below.
7. Near/May Cross range is user supplied percentage.
8. Indicator-vs-indicator comparison is supported, e.g. EMA(50) vs EMA(200).
9. Daily market-data update followed by daily scan at a user-selected time.
10. Scan results are saved by run and can be viewed later.
11. Scanned-stock tracking/analytics is intentionally deferred.
12. APK is an Actions artifact, not committed to the repository. Artifact retention is 7 days.
13. Gradle wrapper is intentionally omitted; GitHub Actions installs Gradle.
14. Provider is isolated so a free data provider can be replaced later.
