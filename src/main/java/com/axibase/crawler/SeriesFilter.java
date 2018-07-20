package com.axibase.crawler;

import lombok.Getter;

import java.util.List;

@Getter
public class SeriesFilter {
    private String minimalObservationEnd;
    private List<Integer> rootCategories;
    private String idFilter;
}
