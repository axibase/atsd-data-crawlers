package com.axibase.crawler.client;

import com.axibase.crawler.model.FredSeries;
import com.axibase.crawler.model.FredSeriesSearchResult;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

/**
 * Search Fred series Method.
 */
public class SeriesSearchMethod extends Method<FredSeriesSearchResult> {
    private static final String SEARCH_TEXT = "search_text";
    private static final String SEARCH_TYPE = "search_type";
    private String searchString;

    private SeriesSearchMethod(WebTarget baseResource) {
        super("fred/series/search", baseResource);
    }

    public static Builder builder(final WebTarget baseResource) {
        return new Builder(baseResource);
    }

    public static class Builder {
        private SeriesSearchMethod instance;

        private Builder(final WebTarget baseResource) {
            this.instance = new SeriesSearchMethod(baseResource);
        }

        public Builder searchString(final String searchString) {
            this.instance.searchString = searchString;
            return this;
        }

        public SeriesSearchMethod build() {
            return instance;
        }
    }

    @Override
    public FredSeriesSearchResult execute() {
        final Response response = getResource()
                .queryParam(SEARCH_TEXT, searchString)
                .queryParam(SEARCH_TYPE, "series_id")
                .request(MediaType.APPLICATION_JSON)
                .get();

        response.bufferEntity();
        return response.readEntity(FredSeriesSearchResult.class);
    }
}
