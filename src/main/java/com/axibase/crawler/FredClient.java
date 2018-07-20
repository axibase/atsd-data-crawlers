package com.axibase.crawler;

import com.axibase.crawler.model.FredCategory;
import com.axibase.crawler.model.FredObservation;
import com.axibase.crawler.model.FredSeries;
import com.axibase.crawler.model.FredSeriesSearchResult;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.axibase.crawler.client.SeriesSearchMethod;

@Slf4j
class FredClient {
    private static final String BASE_URL = "https://api.stlouisfed.org/";

    private static final String CHILDREN_CATEGORIES = "fred/category/children";
    private static final String SERIES_TAGS = "fred/series/tags";
    private static final String CATEGORY_SERIES = "fred/category/series";
    private static final String SERIES_OBSERVATIONS = "fred/series/observations";
    private static final String SERIES_CATEGORIES = "fred/series/categories";
    private static final String CATEGORY = "fred/category";
    private static final String SERIES = "fred/series";
    public static final String ERROR_PARSING_JSON = "Error parsing JSON";

    private ObjectMapper mapper = new ObjectMapper();
    private JsonFactory jsonFactory = mapper.getFactory();
    private Client client = ClientBuilder.newClient();
    private WebTarget resource;

    FredClient(String apiKey) {
        resource = client
                .target(BASE_URL)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json");
    }

    private JsonNode readJsonResponse(String response) {
        try {
            JsonParser parser = jsonFactory.createParser(response);
            return mapper.readTree(parser);
        } catch (IOException e) {
            log.error(ERROR_PARSING_JSON, e);
            return null;
        }
    }

    private JsonNode requestWithParam(String path, String key, String value) {
        String response = resource.path(path)
                .queryParam(key, value)
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        return readJsonResponse(response);
    }

    int[] subCategories(int categoryId) {
        JsonNode json = requestWithParam(
                CHILDREN_CATEGORIES,
                "category_id",
                String.valueOf(categoryId)
        ).path("categories");

        int size = json.size();
        int[] result = new int[size];

        for (int i = 0; i < size; i++) {
            result[i] = json.get(i).get("id").asInt();
        }

        return result;
    }

    List<String> seriesTags(String seriesId) {
        JsonNode json = requestWithParam(
                SERIES_TAGS,
                "series_id",
                seriesId
        ).path("tags");

        List<String> result = new ArrayList<>();

        for (int i = 0; i < json.size(); i++) {
            result.add(json.get(i).get("name").asText());
        }

        return result;
    }

    FredSeries[] categorySeries(int categoryId, int offset) {
        String response = resource.path(CATEGORY_SERIES)
                .queryParam("category_id", String.valueOf(categoryId))
                .queryParam("order_by", "series_id")
                .queryParam("offset", String.valueOf(offset))
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);
        try {
            JsonNode json = readJsonResponse(response);
            if (json == null) {
                return null;
            }

            return mapper.treeToValue(json.path("seriess"), FredSeries[].class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return null;
        }
    }

    FredSeriesSearchResult searchSeries(final String searchString) {
        return SeriesSearchMethod.builder(resource)
                .searchString(searchString)
                .build()
                .execute();
    }

    FredSeries singleSeries(String seriesId) {
        JsonNode json = requestWithParam(
                SERIES,
                "series_id",
                String.valueOf(seriesId)
        ).path("seriess");

        try {
            return mapper.treeToValue(json, FredSeries[].class)[0];
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return null;
        }
    }

    FredObservation[] seriesObservations(String seriesId) {
        JsonNode json = requestWithParam(
                SERIES_OBSERVATIONS,
                "series_id",
                seriesId
        ).path("observations");

        List<FredObservation> result = new ArrayList<>();

        int len = json.size();
        for (int i = 0; i < len; i++) {
            JsonNode singleObservation = json.get(i);
            try {
                FredObservation obs = mapper.treeToValue(singleObservation, FredObservation.class);
                result.add(obs);
            } catch (Exception e) {
                log.error("Error parsing JSON", e);
            }
        }
        FredObservation[] arrayResult = new FredObservation[result.size()];
        result.toArray(arrayResult);
        return arrayResult;
    }

    FredCategory[] seriesCategories(String seriesId) {
        JsonNode json = requestWithParam(
                SERIES_CATEGORIES,
                "series_id",
                seriesId
        ).path("categories");

        try {
            return mapper.treeToValue(json, FredCategory[].class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return null;
        }
    }

    FredCategory getCategory(int categoryId) {
        JsonNode json = requestWithParam(
                CATEGORY,
                "category_id",
                String.valueOf(categoryId)
        ).path("categories");

        try {
            return mapper.treeToValue(json, FredCategory[].class)[0];
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return null;
        }
    }

}
