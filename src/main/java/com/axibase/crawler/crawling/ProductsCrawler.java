package com.axibase.crawler.crawling;

import com.axibase.crawler.common.*;
import com.axibase.crawler.data.PriceData;
import com.axibase.crawler.data.ProductInfo;
import com.axibase.crawler.data.ProductUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

public class ProductsCrawler {

    //region ProductInfo

    public Result<ProductInfo> getProductInfo(String baseUrl, ProductUrl productUrl) {

        HtmlPageLoader loader = new HtmlPageLoader();

        Result<ProductInfo> info;
        URL pageUrl;
        try {
            URL base = new URL(baseUrl);
            pageUrl = new URL(base, productUrl.relativeUrl);
        } catch (MalformedURLException ex) {
            return new Result<>("Url format error", null);
        }

        Result<String> pageLoadingResult = loader.LoadHtml(pageUrl);
        if (pageLoadingResult.errorText != null) {
            return new Result<>(pageLoadingResult.errorText, null);
        }

        info = parseProductInfo(pageLoadingResult.result, productUrl);

        if (info == null) {
            return new Result<>("Unable to get product info", null);
        }

        return info;
    }

    private Result<ProductInfo> parseProductInfo(String htmlPage, ProductUrl productUrl) {

        Document htmlDocument = Jsoup.parse(htmlPage);
        if (htmlDocument == null) return new Result<>("Html parse error", null);

        Result<CategoryContainer> categoriesResult = getCategories(htmlDocument);
        if (categoriesResult == null) {
            return new Result<>("Unable to find categories", null);
        }

        if (categoriesResult.errorText != null) {
            return new Result<>(categoriesResult.errorText, null);
        }
        String category = categoriesResult.result.category;
        String subcategory = categoriesResult.result.subcategory;

        Result<AttributesContainer> attributesResult = getAttributes(htmlDocument);
        if (attributesResult.errorText != null) {
            return new Result<>(attributesResult.errorText, null);
        }
        Map<String, String> attributes = attributesResult.result.attributes;

        return new Result<>(null,
                new ProductInfo(
                        productUrl.id,
                        productUrl.name,
                        productUrl.relativeUrl,
                        category,
                        subcategory,
                        attributes));
    }

    private Result<CategoryContainer> getCategories(Document htmlDocument) {
        Element script = htmlDocument.select("script#productData_").last();
        if (script == null) return new Result<>(null, new CategoryContainer(null, null));
        String scriptText = script.html();
        if (scriptText == null) return new Result<>(null, new CategoryContainer(null, null));

        String productCategorySelector = "category:";
        int productStringIndex = scriptText.indexOf(productCategorySelector);
        if (productStringIndex < 0) return null;

        StringBuilder productCategoryStringBuilder = new StringBuilder();
        // set currentCharIndex on first letter
        int currentCharIndex = productStringIndex + productCategorySelector.length() + 2;

        while (currentCharIndex < scriptText.length()) {
            char currentChar = scriptText.charAt(currentCharIndex);
            if (currentChar == '\'') break;
            productCategoryStringBuilder.append(currentChar);
            currentCharIndex++;
        }

        String[] categories = productCategoryStringBuilder.toString().split("/");

        if (categories.length == 0) {
            return new Result<>(null, new CategoryContainer(null, null));
        }

        if (categories.length == 1) {
            return new Result<>(null, new CategoryContainer(categories[0], null));
        }

        String category = categories[categories.length - 2];
        String subcategory = categories[categories.length - 1];

        return new Result<>(null, new CategoryContainer(category, subcategory));
    }

    private Result<AttributesContainer> getAttributes(Document htmlDocument) {
        HashMap<String, String> attributesMap = new HashMap<>();

        // there are two attribute tables on page, parsing both
        Elements attributesContainers = htmlDocument.select("ul.widget-list");
        if (attributesContainers == null) {
            return new Result<>(null, new AttributesContainer(attributesMap));
        }

        for (Element attributesContainer : attributesContainers) {
            if (attributesContainer == null) continue;

            Elements attributesElements = attributesContainer.children();
            if (attributesElements == null) continue;

            for (Element attributesElement : attributesElements) {
                Elements attributes = attributesElement.children();
                if (attributes == null || attributes.size() < 2) continue;

                String attributeName = attributes.get(0).text();
                if (attributeName == null) continue;
                if (attributeName.endsWith(":")) {
                    attributeName = attributeName.substring(0, attributeName.length() - 1);
                }

                String attributeValue = attributes.get(1).text();
                attributesMap.put(attributeName, attributeValue);
            }
        }

        return new Result<>(null, new AttributesContainer(attributesMap));
    }

    private class CategoryContainer {
        final String category;

        final String subcategory;

        private CategoryContainer(String category, String subcategory) {
            this.category = category;
            this.subcategory = subcategory;
        }
    }

    private class AttributesContainer {
        final Map<String, String> attributes;

        private AttributesContainer(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }

    //endregion

    //region PriceData

    public Result<PriceData> getPriceData(String baseUrl, ProductUrl productUrl) {

        HtmlPageLoader loader = new HtmlPageLoader();

        URL base;
        URL pageUrl;
        try {
            base = new URL(baseUrl);
            pageUrl = new URL(base, productUrl.relativeUrl);
        } catch (MalformedURLException ex) {
            return new Result<>("Url format error", null);
        }

        String[] segments = base.getPath().split("/");
        String zone = segments[segments.length - 1];

        Result<String> pageLoadingResult = loader.LoadHtml(pageUrl);
        if (pageLoadingResult.errorText != null) {
            return new Result<>(String.format("zone: %1s %2s", zone, pageLoadingResult.errorText), new PriceData(productUrl.id, zone, null, null));
        }

        Result<PriceContainer> priceResult = parsePriceData(pageLoadingResult.result);
        if (priceResult.errorText != null) {
            return new Result<>(String.format("zone: %1s %2s", zone, priceResult.errorText), new PriceData(productUrl.id, zone, null, null));
        }

        PriceData result = new PriceData(productUrl.id, zone, priceResult.result.price, priceResult.result.discount);

        return new Result<>(null, result);
    }

    private Result<PriceContainer> parsePriceData(String htmlPage) {

        Document htmlDocument = Jsoup.parse(htmlPage);
        if (htmlDocument == null) return new Result<>("Html parse error", null);

        Element priceDataContainer = htmlDocument.select("div.product-information").first();
        if (priceDataContainer == null) return new Result<>("Price not found", null);

        Element priceContainer = priceDataContainer.select("span.product_price").first();
        if (priceContainer == null) return new Result<>("Price not found", null);

        Elements priceElements = priceContainer.select("span");
        if (priceElements == null || priceElements.size() == 0) return new Result<>("Price not found", null);

        Element regularPriceElement = priceElements.get(0);

        Result<Double> priceResult = parsePrice(regularPriceElement);
        if (priceResult.errorText != null) {
            return new Result<>(priceResult.errorText, null);
        }
        double price = priceResult.result;

        Double discount = null;
        if (priceElements.size() > 2) {
            Result<Double> discountPriceResult = parsePrice(priceElements.last());
            if (discountPriceResult.errorText == null) {
                discount = price - discountPriceResult.result;
            }
        }

        return new Result<>(null, new PriceContainer(price, discount));
    }

    private Result<Double> parsePrice(Element priceElement) {
        String priceText = priceElement.text();
        priceText = priceText.replaceAll(" руб.", "");
        double price;
        try {
            NumberFormat format = NumberFormat.getInstance(new Locale("ru"));
            Number number = format.parse(priceText);
            price = number.doubleValue();
        } catch (ParseException ex) {
            return new Result<>("Price format error", null);
        }

        return new Result<>(null, price);
    }

    private class PriceContainer {
        final double price;

        final Double discount;

        private PriceContainer(double price, Double discount) {
            this.price = price;
            this.discount = discount;
        }
    }

    //endregion
}
