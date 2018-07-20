package com.axibase.crawler;

import com.axibase.crawler.model.FredCategory;
import com.axibase.crawler.model.FredObservation;
import com.axibase.crawler.model.FredSeries;
import com.axibase.tsd.client.AtsdServerException;
import com.axibase.tsd.client.HttpClientManager;
import com.axibase.tsd.client.MetaDataService;
import com.axibase.tsd.model.meta.Metric;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

@Slf4j
class FredLoader {
    private static final SimpleDateFormat FRED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    // For series pagination as given by FRED API
    private static final int MAX_SERIES_LIMIT = 1000;
    private static final int MAX_RETRIES = 5;
    private static final int WRITE_RETRIES = 3;

    private Config config;
    private List<Integer> rootCategories;
    private FredClient fredClient;
    private AtsdWriter atsdWriter;
    private Date filterLimit;
    private HttpClientManager httpClientManager;
    private MetaDataService metaDataService;

    @Getter
    private List<String> updatedSeries = new ArrayList<>();
    @Getter
    private List<String> newSeries = new ArrayList<>();

    FredLoader(Config config,
               FredClient fredClient,
               HttpClientManager httpClientManager) throws Exception {
        this.config = config;
        this.fredClient = fredClient;
        this.httpClientManager = httpClientManager;
        this.atsdWriter = new AtsdWriter(httpClientManager);
        this.atsdWriter.setTracing(config.getTraceCommands());
        this.metaDataService = new MetaDataService(this.httpClientManager);
        this.filterLimit = FRED_DATE_FORMAT.parse(config.getFilter().getMinimalObservationEnd());
    }

    private Set<FredSeries> fetchSeries(final SeriesFilter filter) {
        Set<FredSeries> allSeries = new HashSet<>();
        if (filter.getRootCategories() != null) {
            Set<Integer> allCategories = new HashSet<>(fetchSubcategories(rootCategories));
            for (int category : allCategories) {
                allSeries.addAll(fetchSeriesForCategory(category));
            }
        } else if (filter.getIdFilter() != null) {
            return fredClient.searchSeries(filter.getIdFilter()).getSeriess();
        } else {
            throw new IllegalStateException("Incorrect search filter");
        }
        return allSeries;
    }


    void runLoading() {
        Set<FredSeries> allSeries = fetchSeries(config.getFilter());
        for (FredSeries series : allSeries) {
            log.info("Found series with id " + series.getId());
        }

        for (FredSeries series : allSeries) {
            int retries = MAX_RETRIES;
            while (retries > 0) {
                try {
                    fetchAndWriteSeries(series);
                    break;
                } catch (Exception e) {
                    log.error("Error fetching series", e);
                }
                retries--;
                if (retries == 0) {
                    log.info("Can't fetch series {}, giving up", series.getId());
                } else {
                    log.info("Retrying to fetch {}", series.getId());
                }
            }
        }
        log.info("Finished loading series");
    }

    private List<Integer> fetchSubcategories(Collection<Integer> catIds) {
        Set<Integer> foundCategories = new HashSet<>(catIds);
        List<Integer> discoveryQueue = new LinkedList<>(catIds);

        while (!discoveryQueue.isEmpty()) {
            int category = discoveryQueue.remove(0);

            log.info("Fetching subcategories for category #" + category);
            int[] subcategories = fredClient.subCategories(category);
            for (int subcategory : subcategories) {
                if (!foundCategories.contains(subcategory)) {
                    foundCategories.add(subcategory);
                    discoveryQueue.add(subcategory);
                }
            }
        }

        return new ArrayList<>(foundCategories);
    }

    private List<FredSeries> fetchSeriesForCategory(int categoryId) {
        List<FredSeries> series = new ArrayList<>();
        log.info("Fetching category #" + categoryId);
        int offset = 0;
        while (true) {
            FredSeries[] seriesOfCategory = fredClient.categorySeries(categoryId, offset);
            series.addAll(Arrays.asList(seriesOfCategory));
            offset += MAX_SERIES_LIMIT;
            if (seriesOfCategory.length < MAX_SERIES_LIMIT) {
                break;
            }
        }
        return series;
    }

    @FunctionalInterface
    public interface FunctionWithException<T, R, E extends Exception> {
        R apply(T t) throws E;
    }

    private <T, R, E extends Exception> Function<T, R> wrapper(FunctionWithException<T, R, E> fe) {
        return arg -> {
            try {
                return fe.apply(arg);
            } catch (Exception e) {
                return null;
            }
        };
    }

    private void fetchAndWriteSeries(FredSeries series) {
        String seriesObservationEnd = series.getObservationEnd();
        Date seriesObservationEndDate;
        try {
            seriesObservationEndDate = FRED_DATE_FORMAT.parse(seriesObservationEnd);
        } catch (ParseException e) {
            log.error("Error parsing end date", e);
            return;
        }
        if (seriesObservationEndDate.before(filterLimit)) {
            log.info("{},skip,,{}", series.getId(), seriesObservationEnd);
            return;
        }
        final Optional<Metric> metric = Optional.of(series)
                .map(FredSeries::getId)
                .map(wrapper(id -> metaDataService.retrieveMetric(id)));

        if (metric.isPresent()) {
            final Metric storedMetric = metric.get();
            Map<String, String> metricTags = storedMetric.getTags();
            String storedObservationEnd = metricTags.get("observation_end");
            Date storedEndDate = null;
            try {
                storedEndDate = FRED_DATE_FORMAT.parse(storedObservationEnd);
            } catch (ParseException e) {
                log.error("Error parsing observation_end", e);
            }

            if (storedEndDate == null || storedEndDate.before(seriesObservationEndDate)) {
                log.info("{},update,{},{}",
                        series.getId(), FRED_DATE_FORMAT.format(storedEndDate), seriesObservationEnd);
                updatedSeries.add(series.getId());
            } else {
                log.info("{},skip,{},{}",
                        series.getId(), FRED_DATE_FORMAT.format(storedEndDate), seriesObservationEnd);
                return;
            }
        } else {
            log.info("{},create,,{}", series.getId(), seriesObservationEnd);
            newSeries.add(series.getId());
        }

        FredCategory[] seriesCats = fredClient.seriesCategories(series.getId());
        int maxCatIndex = 0;
        for (int i = 0; i < seriesCats.length; i++) {
            if (seriesCats[maxCatIndex].getParentId() < seriesCats[i].getParentId()) {
                maxCatIndex = i;
            }
        }
        FredCategory maxCategory = seriesCats[maxCatIndex];
        FredCategory parentCategory = fredClient.getCategory(maxCategory.getParentId());
        List<String> tags = fredClient.seriesTags(series.getId());
        FredObservation[] observations = fredClient.seriesObservations(series.getId());


        boolean added = false;
        int i = 0;
        while (!added || i < WRITE_RETRIES) {
            try {
                atsdWriter.writeFredSeries(series, maxCategory, parentCategory, tags, observations);
                added = true;
            } catch (Exception e) {
                log.error("Error writing series " + series.getId(), e);
            }
            i++;
        }
    }
}
