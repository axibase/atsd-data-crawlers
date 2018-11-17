# IRS statistics data crawler

Crawler that collects data from https://www.irs.gov/uac/2017-and-prior-year-filing-season-statistics and converts it to ATSD series commands

## Usage

Download source code

```sh
git clone https://github.com/axibase/atsd-data-crawlers
cd atsd-data-crawlers/
git checkout irs-crawler
```

Build using Maven

```sh
mvn clean install
```

For crawling all statistics, run

```sh
mvn exec:java
```

For crawling statistics from specitic date use "-d" flag (-d yyyy-MM-dd)

```sh
mvn exec:java -Dexec.args="-d 2016-01-01"
```

The result is "series.txt" file which contains series commands.
