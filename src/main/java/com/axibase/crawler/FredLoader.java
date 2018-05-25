package com.axibase.crawler;

import com.axibase.crawler.model.FredCategory;
import com.axibase.crawler.model.FredObservation;
import com.axibase.crawler.model.FredSeries;
import com.axibase.tsd.client.HttpClientManager;
import com.axibase.tsd.client.MetaDataService;
import com.axibase.tsd.model.meta.Metric;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

class FredLoader {
    private static final Logger logger = LoggerFactory.getLogger(FredLoader.class);
    // For series pagination as given by FRED API
    private static final int MAX_SERIES_LIMIT = 1000;
    private static final int MAX_RETRIES = 5;
    public static final String FRED_DATE_PATTERN = "yyyy-MM-dd";

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
               HttpClientManager httpClientManager) throws IOException, ParseException {
        this.config = config;
        this.rootCategories = config.getRootCategories();
        this.fredClient = fredClient;
        this.httpClientManager = httpClientManager;
        this.atsdWriter = new AtsdWriter(httpClientManager);
        this.atsdWriter.setTracing(config.getTraceCommands());
        this.metaDataService = new MetaDataService(this.httpClientManager);
        this.filterLimit = parseDate(config.getMinimalObservationEnd());
    }

    private Date parseDate(final String formattedDate) throws ParseException {
        return new SimpleDateFormat(FRED_DATE_PATTERN).parse(formattedDate);
    }

    void runLoading() {
        Set<Integer> allCategories = new HashSet<>(fetchSubcategories(rootCategories));
        Set<FredSeries> allSeries = new HashSet<>();

        for (int category : allCategories) {
            allSeries.addAll(fetchSeriesForCategory(category));
        }

        for (FredSeries series : allSeries) {
            logger.info("Found series with id {}", series.getId());
        }
        List<String> ids = Arrays.asList();
        final List<String> seriesFilter = config.getSeriesFilter();
        if (seriesFilter != null) {
            logger.info("Applying log filter: {}", seriesFilter);
            allSeries = allSeries.stream()
                    .filter(fs -> ids.contains(fs.getId()))
                    .collect(Collectors.toSet());
        }
        for (FredSeries series : allSeries) {
            int retries = MAX_RETRIES;
            while (retries > 0) {
                try {
                    fetchAndWriteSeries(series);
                    break;
                } catch (Exception e) {
                    logger.error("Error fetching series", e);
                }
                retries--;
                if (retries == 0) {
                    logger.info("Can't fetch series {}, giving up", series.getId());
                } else {
                    logger.info("Retrying to fetch {}", series.getId());
                }
            }
        }
        logger.info("Finished loading series");
    }

    private List<Integer> fetchSubcategories(Collection<Integer> catIds) {
        Set<Integer> foundCategories = new HashSet<>(catIds);
        List<Integer> discoveryQueue = new LinkedList<>(catIds);

        while (!discoveryQueue.isEmpty()) {
            int category = discoveryQueue.remove(0);

            logger.info("Fetching subcategories for category #{}", category);
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
        logger.info("Fetching category #{}", categoryId);
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

    private void fetchAndWriteSeries(FredSeries series) {
        String seriesObservationEnd = series.getObservationEnd();
        Date seriesObservationEndDate;
        try {
            seriesObservationEndDate = parseDate(seriesObservationEnd);
        } catch (ParseException e) {
            logger.error("Error parsing end date", e);
            return;
        }
        if (seriesObservationEndDate.before(filterLimit)) {
            logger.info("{},skip,,{}", series.getId(), seriesObservationEnd);
            return;
        }

        Metric storedMetric = metaDataService.retrieveMetric(series.getId());
        if (storedMetric != null) {
            Map<String, String> metricTags = storedMetric.getTags();
            String storedObservationEnd = metricTags.get("observation_end");
            Date storedEndDate = null;
            try {
                storedEndDate = parseDate(storedObservationEnd);
            } catch (ParseException e) {
                logger.error("Error parsing observation_end", e);
            }

            if (storedEndDate == null || storedEndDate.before(seriesObservationEndDate)) {
                logger.info("{},update,{},{}",
                        series.getId(), storedObservationEnd, seriesObservationEnd);
                updatedSeries.add(series.getId());
            } else {
                logger.info("{},skip,{},{}",
                        series.getId(), seriesObservationEnd, seriesObservationEnd);
                return;
            }
        } else {
            logger.info("{},create,,{}", series.getId(), seriesObservationEnd);
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

        try {
            atsdWriter.writeFredSeries(series, maxCategory, parentCategory, tags, observations);
        } catch (Exception e) {
            logger.error("Error writing series " + series.getId(), e);
        }
    }
}
