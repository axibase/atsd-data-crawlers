package com.axibase.crawler.orders;

import com.axibase.crawler.common.Result;
import com.axibase.crawler.data.OrderData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class OrderInfoParser {

    public Result<ArrayList<OrderData>> readOrders(String orderFilesDirectory) {
        File directory = new File(orderFilesDirectory);
        File[] htmlFiles = directory.listFiles();

        if (htmlFiles == null) {
           return new Result<>("Cannot access specified directory", null);
        }

        ArrayList<OrderData> orders = new ArrayList<>();
        OrderInfoParser parser = new OrderInfoParser();
        for (File file : htmlFiles) {
            Result<OrderData> productResult = parser.getProductInfo(file);
            if (productResult == null) {
                return new Result<>(String.format("File structure error %1s", file.getName()), null);
            }

            if (productResult.errorText != null) {
                return new Result<>(String.format("File %1s parse error: %2s", file.getName(), productResult.errorText), null);
            }

            orders.add(productResult.result);
        }

        return new Result<>(null, orders);
    }

    private Result<OrderData> getProductInfo(File htmlFile) {

        Document document;

        try {
            document = Jsoup.parse(htmlFile, "UTF-8");
        } catch (IOException ex) {
            return null;
        }

        if (document == null) return new Result<>("Document parse error", null);

        Element orderIdElement = document.select("div.order-number").first();
        if (orderIdElement == null) return new Result<>("Unable to find order id", null);

        Element orderIdContainer = orderIdElement.select("span.text").first();
        if (orderIdContainer == null) return new Result<>("Unable to find order id", null);

        int orderId;
        try {
            orderId = Integer.parseInt(orderIdContainer.text());
        } catch (NumberFormatException ex) {
            return new Result<>("Unable to parse order id", null);
        }

        Element orderDateElement = document.select("div.date-order").first();
        if (orderDateElement == null) return new Result<>("Unable to find order date", null);

        Element orderDateContainer = orderDateElement.select("span.text").first();
        if (orderDateContainer == null) return new Result<>("Unable to find order date", null);

        Date orderDate;
        try {
            SimpleDateFormat parser=new SimpleDateFormat("dd.MM.yyyy");
            orderDate = parser.parse(orderDateContainer.text());
        } catch (ParseException ex) {
            return new Result<>("Unable to parse order date", null);
        }

        Element productTable = document.select("table.cart-items").first();
        if (productTable == null) return new Result<>("Products parse error", null);

        Element tableBody = productTable.child(0);
        if (tableBody == null || tableBody.children() == null) return new Result<>("Products parse error", null);

        ArrayList<Element> productIds = new ArrayList<>();
        ArrayList<Element> productDescriptions = new ArrayList<>();
        for (Element element : tableBody.children()) {
            // Skip table header
            if (element.className().equals("even")) {
                continue;
            }

            if (element.nodeName().equals("tr")) {
                productDescriptions.add(element);
                continue;
            }

            if (element.nodeName().equals("script")) {
                productIds.add(element);
            }
        }

        if (productIds.size() != productDescriptions.size()) return new Result<>("Products parse error", null);

        ArrayList<ProductElement> productElements = new ArrayList<>(productIds.size());
        for (int i = 0; i < productIds.size(); i++) {
            Element id = productIds.get(i);
            Element description = productDescriptions.get(i);
            productElements.add(new ProductElement(id, description));
        }

        ArrayList<OrderInfo> products = new ArrayList<>(productElements.size());

        for (ProductElement productElement : productElements) {
            if (productElement == null) {
                return new Result<>("Unable to find product element", null);
            }

            Integer productId = getId(productElement.idElement);
            if (productId == null) {
                return new Result<>("Unable to get id", null);
            }

            Elements productProperties = productElement.descriptionElement.select("td");
            if (productProperties == null || productProperties.size() < 3) {
                return new Result<>("Invalid properties count", null);
            }

            Element nameElement = productProperties.get(0);
            if (nameElement == null) {
                return new Result<>("Unable to get name element", null);
            }

            String name = getName(nameElement);
            if (name == null) {
                return new Result<>("Unable to get name", null);
            }

            Element amountElement = productProperties.get(1);
            if (amountElement == null) {
                return new Result<>("Unable to get amount element", null);
            }

            Double amount = getAmount(amountElement);
            if (amount == null) {
                return new Result<>("Unable to get amount", null);
            }

            String units = getUnits(amountElement);
            if (units == null) {
                return new Result<>("Unable to get units", null);
            }

            Element priceElement = productProperties.get(2);
            if (priceElement == null) {
                return new Result<>("Unable to get price element", null);
            }

            Double price = getPrice(priceElement);
            if (price == null) {
                return new Result<>("Unable to get price", null);
            }

            products.add(new OrderInfo(orderDate, productId, name, amount, price, units));
        }

        return new Result<>(null, new OrderData(orderId, orderDate, products));
    }

    private static Integer getId(Element idElement) {

        String scriptText = idElement.html();
        String productIdSelector = "id:";
        int productStringIndex = scriptText.indexOf(productIdSelector);
        if (productStringIndex < 0) return null;

        StringBuilder productIdStringBuilder = new StringBuilder();
        // set currentCharIndex on first id digit
        int currentCharIndex = productStringIndex + productIdSelector.length() + 1;

        // walking to first id digit
        while (currentCharIndex < scriptText.length()) {
            char currentChar = scriptText.charAt(currentCharIndex);
            if (Character.isDigit(currentChar)) break;
            currentCharIndex++;
        }

        while (currentCharIndex < scriptText.length()) {
            char currentChar = scriptText.charAt(currentCharIndex);
            if (currentChar == '\'') break;
            productIdStringBuilder.append(currentChar);
            currentCharIndex++;
        }

        int id;
        try {
            id = Integer.parseInt(productIdStringBuilder.toString());
        } catch (NumberFormatException ex) {
            return null;
        }

        return id;
    }

    private static String getName(Element nameElement) {
        Element titleContainer = nameElement.child(0);
        if (titleContainer == null) return null;

        Element title = titleContainer.child(0);
        if (title == null) return null;

        return title.text();
    }

    private static Double getAmount(Element amountElement) {
        Element amount = amountElement.child(0);
        if (amount == null) return null;

        String amountText = amount.ownText();

        try {
            NumberFormat format = NumberFormat.getInstance(new Locale("ru"));
            Number number = format.parse(amountText);
            return number.doubleValue();
        } catch (Exception e) {
            // skip
        }

        // trying to parse in default locale
        try {
            return Double.parseDouble(amountText);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double getPrice(Element priceElement) {
        Element price = priceElement.child(0);
        if (price == null) return null;

        String priceText = price.ownText();
        if (priceText == null) return null;

        priceText = priceText.replaceAll("[^\\d,]", "");

        double result;
        try {
            NumberFormat format = NumberFormat.getInstance(new Locale("ru"));
            Number number = format.parse(priceText);
            result = number.doubleValue();
        } catch (ParseException ex) {
            return null;
        }

        return result;
    }

    private static String getUnits(Element amountElement) {
        Element units = amountElement.select("span").first();
        if (units == null) return null;

        String unitsText = units.ownText();
        if (unitsText == null) return null;

        unitsText = unitsText.replace(".", "");
        return unitsText;
    }

    private class ProductElement {
        final Element idElement;

        final Element descriptionElement;

        private ProductElement(Element idElement, Element descriptionElement) {
            this.idElement = idElement;
            this.descriptionElement = descriptionElement;
        }
    }
}
