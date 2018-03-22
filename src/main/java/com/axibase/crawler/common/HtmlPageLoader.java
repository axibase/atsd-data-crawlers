package com.axibase.crawler.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

public class HtmlPageLoader {
    public Result<String> LoadHtml(URL url) {

        Result<String> pageLoadingResult = null;
        int triesCount = 3;
        while (triesCount > 0)
        {
            pageLoadingResult = loadPage(url);
            // wait for reduce server load
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (pageLoadingResult != null && pageLoadingResult.errorText == null)
            {
                break;
            }
            triesCount--;
        }

        if (pageLoadingResult == null)
        {
            return new Result<>("Network error", null);
        }

        if (pageLoadingResult.errorText != null)
        {
            return new Result<>(pageLoadingResult.errorText, null);
        }

        return new Result<>(null, pageLoadingResult.result);
    }

    private Result<String> loadPage(URL url)
    {
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            return new Result<>("Network error", null);
        }

        try {
            connection.setRequestMethod("GET");
        } catch (ProtocolException ex) {
            return new Result<>("Protocol error", null);
        }

        StringBuilder builder = new StringBuilder();
        try {
            try (InputStream stream = connection.getInputStream()) {
                try (InputStreamReader streamReader = new InputStreamReader(stream)) {
                    BufferedReader reader = new BufferedReader(streamReader);

                    String inputLine;
                    while ((inputLine = reader.readLine()) != null)
                        builder.append(inputLine);
                }
            }
        } catch (IOException ex) {
            return new Result<>("Network error", null);
        }

        return new Result<>(null, builder.toString());
    }
}
