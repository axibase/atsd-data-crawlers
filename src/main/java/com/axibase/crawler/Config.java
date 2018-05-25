package com.axibase.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Getter
class Config {
    private static final String CONFIG_FILE = "config.json";

    private String minimalObservationEnd;
    private String apiKey;
    private List<Integer> rootCategories;
    private Boolean traceCommands;
    private List<String> seriesFilter;

    static Config fromFile() throws IOException {
        return fromFile(CONFIG_FILE);
    }

    private static Config fromFile(String filePath) throws IOException {
        File configFile = new File(filePath);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(configFile, Config.class);
    }

    public void setTraceCommands(Boolean traceCommands) {
        this.traceCommands = traceCommands;
    }

    Boolean getTraceCommands() {
        return traceCommands != null && traceCommands;
    }
}
