# IRS Expatriation Statistics data crawler

## Usage

Clone repository

```sh
git clone -b irs-expatriation-data-crawler https://github.com/axibase/atsd-data-crawlers
```

```sh
cd atsd-data-crawlers
```

Modify links in the `urls.properties` file.

Start crawler using maven.

```
mvn compile exec:java
```

Series commands are created in the `result` folder.
