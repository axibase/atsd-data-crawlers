package com.axibase.crawler;

import com.axibase.crawler.data.FredCategory;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FredClient {
    private static final String BASE_URL = "https://api.stlouisfed.org/";

    private static final String CHILDREN_CATEGORIES = "fred/category/children";
    private static final String CATEGORY = "fred/category";

    private ObjectMapper mapper = new ObjectMapper();
    private JsonFactory jsonFactory = mapper.getFactory();
    private Client client = Client.create();
    private WebResource resource;

    FredClient(String apiKey) {
        resource = client
                .resource(BASE_URL)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json");
    }

    private JsonNode readJsonResponse(ClientResponse response) {
        String jsonString = response.getEntity(String.class);
        try {
            JsonParser parser = jsonFactory.createParser(jsonString);
            return mapper.readTree(parser);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JsonNode requestWithParam(String path, String key, String value) {
        ClientResponse response = resource.path(path)
                .queryParam(key, value)
                .accept("application/json")
                .get(ClientResponse.class);

        return readJsonResponse(response);
    }

    FredCategory[] subCategories(int categoryId) {
        JsonNode json = requestWithParam(
                CHILDREN_CATEGORIES,
                "category_id",
                String.valueOf(categoryId)
        ).path("categories");

        try {
            return mapper.treeToValue(json, FredCategory[].class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    List<FredCategory> getRootCategories(final List<Integer> ids) {
        if (ids.size() == 1 && ids.get(0) == 0) {
            return Arrays.asList(this.subCategories(0));
        }
        List<FredCategory> result = new ArrayList<>();
        ids.forEach(id -> {if (id != 0) result.add(this.getCategory(id));});
        return result;
    }

}