package com.axibase.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.io.File;
import java.io.IOException;

@Getter
class Config {
    private static final String CONFIG_FILE = "config.json";

    private String apiKey;
    private Boolean traceCommands;
    private SeriesFilter filter;

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
