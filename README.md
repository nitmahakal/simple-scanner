# NSE Simple Scanner

Fresh standalone Android project. It does not reuse the old NSE Research App.

## V1
- NSE equity universe refreshed from NSE equity list.
- Daily market-data update through a replaceable provider layer.
- Daily / Weekly / Monthly close scanning.
- EMA, HMA, RSI, EMA of RSI, MACD, Stoch RSI K/D, numeric value, Reverse RSI, Reverse Stoch RSI price/K/D.
- Comparators: Above, Below, Equal, Cross Above, Cross Below, Near By, May Go To Cross Above, May Go To Cross Below.
- Near / May Cross percentages are user-entered.
- AND / OR condition sets with up to 3 conditions in the first UI.
- Daily scheduled update followed by scan.
- Scan result history is saved and viewable.
- Scanned-stock tracking is deliberately NOT included yet.

## GitHub
Create a brand-new empty repository and upload the extracted ZIP contents to `main` in one initial commit.
Do not upload `build/`, `.gradle/`, APKs, caches, or generated files.
There is intentionally no Gradle wrapper in this first package; GitHub Actions installs Gradle 8.7.

## Data note
The first provider uses public Yahoo chart responses and can be rate limited. The provider is isolated so it can be replaced later without rewriting the scanner engine.
