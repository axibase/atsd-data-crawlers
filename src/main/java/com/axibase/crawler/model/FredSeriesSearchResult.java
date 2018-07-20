package com.axibase.crawler.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

import java.util.Set;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
public class FredSeriesSearchResult {
    private String realtimeStart;
    private String realtimeEnd;
    private String orderBy;
    private String sortOrder;
    private Long count;
    private Long offset;
    private Long limit;
    private Set<FredSeries> seriess;
}
