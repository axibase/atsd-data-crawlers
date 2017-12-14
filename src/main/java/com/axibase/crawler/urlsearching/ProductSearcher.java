package com.axibase.crawler.urlsearching;

import com.axibase.crawler.common.HtmlPageLoader;
import com.axibase.crawler.common.Result;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;

class ProductSearcher {
    Result<String> searchProductUrl(int storeId, String productName) {
        String urlString = "https://www.okeydostavka.ru/webapp/wcs/stores/servlet/SearchDisplay?categoryId=&catalogId=12052&langId=-20&sType=SimpleSearch&resultCatEntryType=2&showResultsPage=true&searchSource=Q&pageView=&beginIndex=0&pageSize=20&orderBy=2";
        URL url;
        try {
            String[] nameParts = productName.split(" ");
            StringBuilder name = new StringBuilder();
            if (nameParts.length <= 4) {
                name.append(productName);
            } else {
                for (int i = 0; i < nameParts.length - 2; i++) {
                    name.append(nameParts[i]);
                    if (i != nameParts.length - 3) {
                        name.append(" ");
                    }
                }
            }

            String request = URLEncoder.encode(name.toString(), "UTF-8");
            String uriBuilder = urlString + "&searchTerm=" +
                    request +
                    "&storeId=" +
                    storeId;

            url = new URL(uriBuilder);

        } catch (MalformedURLException | UnsupportedEncodingException ex) {
            return new Result<>("Url error", null);
        }

        HtmlPageLoader loader = new HtmlPageLoader();
        Result<String> pageLoadingResult = loader.LoadHtml(url);
        if (pageLoadingResult.errorText != null) return new Result<>(pageLoadingResult.errorText, null);

        return getProductUrl(productName, pageLoadingResult.result);
    }

    private Result<String> getProductUrl(String productName, String html)
    {
        Document htmlDocument = Jsoup.parse(html);
        if (htmlDocument == null) return new Result<>("Html parse error", null);

        Elements productContainers = htmlDocument.select("div.ok-theme.product");
        if (productContainers == null) {
            return new Result<>("Product not found", null);
        }

        String relativeUrl = null;
        for (Element productContainer : productContainers) {

            if (productContainer == null) continue;

            Element nameContainer = productContainer.select("div.product_name").first();
            if (nameContainer == null) continue;

            Element urlElement = nameContainer.children().first();
            if (urlElement == null) continue;


            String urlElementHtml = urlElement.outerHtml();
            if (urlElementHtml == null) continue;

            // manually extracting "title" element because of invalid double quote escaping
            String titleSelector = "title=";
            int titleIndex = urlElementHtml.indexOf(titleSelector);
            if (titleIndex < 0) continue;
            urlElementHtml = urlElementHtml.substring(titleIndex + titleSelector.length());

            String scriptSelector = " onclick";
            int scriptIndex = urlElementHtml.indexOf(scriptSelector);
            if (scriptIndex < 0) continue;
            urlElementHtml = urlElementHtml.substring(0, scriptIndex);

            urlElementHtml = StringEscapeUtils.unescapeHtml4(urlElementHtml);

            StringBuilder titleBuilder = new StringBuilder(urlElementHtml.length());
            for (int i = 0; i < urlElementHtml.length(); i++) {
                char currentChar = urlElementHtml.charAt(i);
                if (currentChar == '"' || currentChar == ' ') continue;
                currentChar = Character.toLowerCase(currentChar);
                titleBuilder.append(currentChar);
            }
            String title = titleBuilder.toString();

            StringBuilder safeProductNameBuilder = new StringBuilder(productName.length());
            for (int i = 0; i < productName.length(); i++) {
                char currentChar = productName.charAt(i);
                if (currentChar == '"' || currentChar == ' ') continue;
                currentChar = Character.toLowerCase(currentChar);
                safeProductNameBuilder.append(currentChar);
            }
            String safeProductName = safeProductNameBuilder.toString();

            // Searching for product name full match
            if (!title.equals(safeProductName)) continue;

            relativeUrl = urlElement.attr("href");
            if (relativeUrl == null) {
                return new Result<>("Url not found", null);
            }

            break;
        }

        if (relativeUrl == null) return new Result<>("Product not found", null);

        if (relativeUrl.length() < 1) return new Result<>("Invalid url", null);
        //removing city identifier in url
        // walking to slash after city identifier
        int currentCharIndex = 1;
        while (currentCharIndex < relativeUrl.length()) {
            if (relativeUrl.charAt(currentCharIndex) == '/') break;
            currentCharIndex++;
        }

        if (currentCharIndex >= relativeUrl.length()) return new Result<>("Invalid url", null);

        relativeUrl = relativeUrl.substring(currentCharIndex + 1);

        return new Result<>(null, relativeUrl);
    }
}

