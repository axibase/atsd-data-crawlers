# IRS Filing Season Statistics data crawler

## Usage

Clone repository

```
git clone -b irs-expatriation-data-crawler https://github.com/axibase/atsd-data-crawlers
cd atsd-data-crawlers
```

Add links on XML files to urls.properties file (in property `urls`)

Start crawler using maven

```
mvn compile exec:java
```

Result series commands would be created in `result` folder