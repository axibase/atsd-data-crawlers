package com.axibase.crawler.common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationManager {

    public Config loadConfig() throws IOException {

        Properties prop = new Properties();

        try {

            String propFileName = "config.properties";

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName)) {

                if (inputStream != null) {
                    prop.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
                }
            }

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        String host = prop.getProperty("host");
        int queryPort = Integer.parseInt(prop.getProperty("query_port"));
        int tcpPort = Integer.parseInt(prop.getProperty("tcp_port"));
        String user = prop.getProperty("user");
        String password = prop.getProperty("password");
        String ordersDirectory = prop.getProperty("orders_dir");
        String[] baseUrls = prop.getProperty("base_urls").split(",");

        return new Config(host, queryPort, tcpPort, user, password, ordersDirectory, baseUrls);
    }
}