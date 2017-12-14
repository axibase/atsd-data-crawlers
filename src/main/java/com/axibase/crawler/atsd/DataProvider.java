package com.axibase.crawler.atsd;

import com.axibase.crawler.common.Logger;
import com.axibase.crawler.common.Result;
import com.axibase.crawler.data.ProductData;
import com.axibase.crawler.data.PriceData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class DataProvider {
    private String host;
    private int queryPort;
    private String user;
    private String password;

    private AtsdTcpClient client;
    private boolean started;

    public DataProvider(
            String host,
            int queryPort,
            int tcpPort,
            String user,
            String password) {
        this.host = host;
        this.queryPort = queryPort;
        this.user = user;
        this.password = password;
        client = new AtsdTcpClient(host, tcpPort);
        started = false;
    }

    public Result<Boolean> start() {
        try {
            client.init();
        } catch (IOException e) {
            started = false;
            return new Result<>(e.getMessage(), false);
        }

        started = true;
        return new Result<>(null, true);
    }

    public void stop() {
        client.shutdown();
        started = false;
    }

    public Result<Boolean> updateProduct(ProductData product) {
        if (!started) return new Result<>("Provider not started", false);
        if (product == null) return new Result<>("Bad request", false);

        DateTime date = DateTime.now().withZone(DateTimeZone.UTC).withTimeAtStartOfDay().toDateTimeISO();
        String dateString = date.toString();

        try {
            String entity = String.valueOf(product.info.id);
            HashMap<String, String> entityTags = new HashMap<>();
            String path = product.info.url.startsWith("/") ? product.info.url : "/" + product.info.url;
            entityTags.put("path", path);

            if (product.info.category != null) {
                entityTags.put("category", product.info.category);
            }

            if (product.info.subcategory != null) {
                entityTags.put("subcategory", product.info.subcategory);
            }

            entityTags.put("units", product.units);

            entityTags.put("in_last_order", String.valueOf(product.isInLastOrder));

            client.sendEntity(entity, product.info.name, entityTags);

            String propertyType = "okey";
            if (product.info.attributes != null && product.info.attributes.size() > 0) {
                client.sendProperty(dateString, entity, propertyType, product.info.attributes);
            }

            for (PriceData data : product.priceData) {
                double price = data.price != null ? data.price : product.lastPrice;
                double discount;
                if (data.discount != null) {
                    discount = data.discount;
                } else if (data.price != null) {
                    discount = 0;
                } else {
                    discount = product.lastDiscount;
                }

                updatePrice(
                        data.id,
                        price,
                        discount,
                        data.zone,
                        data.price != null);
            }

        } catch (Exception ex) {
            return new Result<>(ex.getMessage(), false);
        }

        return new Result<>(null, true);
    }

    public Result<Boolean> updateOrderInfo(int productId, double price, double amount, Date date, boolean isInLastOrder) {
        if (!started) return new Result<>("Provider not started", false);

        DateTime utcDate = new DateTime(date).withZone(DateTimeZone.UTC).withTimeAtStartOfDay().toDateTimeISO();
        String dateString = utcDate.toString();

        try {

            String entity = String.valueOf(productId);

            client.sendSeries(
                    dateString,
                    entity,
                    "okey_original_price",
                    price,
                    null
            );

            client.sendSeries(
                    dateString,
                    entity,
                    "okey_amount",
                    amount,
                    null
            );

            HashMap<String, String> properties = new HashMap<>();

            properties.put("in_last_order", String.valueOf(isInLastOrder));

            client.sendEntity(
                    entity,
                    properties);

        } catch (IOException e) {
            return new Result<>(e.getMessage(), false);
        }

        return new Result<>(null, true);
    }

    public Result<Boolean> updatePrice(int productId, double lastPrice, Double discount, String zone, boolean productAvailable) {
        if (!started) return new Result<>("Provider not started", false);

        DateTime date = DateTime.now().withZone(DateTimeZone.UTC).withTimeAtStartOfDay().toDateTimeISO();
        String dateString = date.toString();

        try {

            sendPrice(dateString, productId, lastPrice, zone);

            if (discount != null) {
                sendDiscount(dateString, productId, discount, zone);
            }
            else {
                sendDiscount(dateString, productId, 0.0, zone);
            }

            HashMap<String, String> properties = new HashMap<>();

            properties.put("is_available", String.valueOf(productAvailable));

            client.sendEntity(
                    String.valueOf(productId),
                    properties);

        } catch (IOException e) {
            return new Result<>(e.getMessage(), false);
        }

        return new Result<>(null, true);
    }

    private void sendPrice(String isoDateString, int productId, double lastPrice, String zone) throws IOException {
        String entity = String.valueOf(productId);
        HashMap<String, String> tags = new HashMap<>();
        tags.put("store", zone);

        client.sendSeries(
                isoDateString,
                entity,
                "okey_price",
                lastPrice,
                tags
        );
    }

    private void sendDiscount(String isoDateString, int productId, Double discount, String zone) throws IOException {
        String entity = String.valueOf(productId);
        HashMap<String, String> tags = new HashMap<>();
        tags.put("store", zone);

        client.sendSeries(
                isoDateString,
                entity,
                "okey_discount",
                discount,
                tags
        );
    }

    public Result<HashMap<Integer, PriceStatistics>> getPriceStatistics() {
        String sqlUrl = "jdbc:atsd://" + host + ":" + queryPort;
        String query = "SELECT \n" +
                        "    okey_price.entity, \n" +
                        "    LAST(okey_price.value),\n" +
                        "    LAST(okey_discount.value)\n" +
                        "FROM okey_price\n" +
                        "OUTER JOIN USING entity okey_discount\n" +
                        "GROUP BY okey_price.entity";

        HashMap<Integer, PriceStatistics> result = new HashMap<>();
        Connection connection = null;
        try {
            Statement statement;
            connection = DriverManager.getConnection(sqlUrl, user, password);
            statement = connection.createStatement();
            try (ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    String productIdString = resultSet.getString(1);

                    int productId;
                    try {
                        productId = Integer.parseInt(productIdString);
                    } catch (NumberFormatException ex) {
                        return new Result<>(String.format("Product ID format error %1s", productIdString), null);
                    }

                    String priceString = resultSet.getString(2);
                    if (priceString == null) continue;

                    double price;
                    try {
                        price = Double.parseDouble(priceString);
                    } catch (NumberFormatException ex) {
                        return new Result<>(String.format("Product price format error %1s", priceString), null);
                    }

                    if (Double.compare(price, Double.NaN) == 0) continue;

                    String discountString = resultSet.getString(3);
                    if (discountString == null) continue;

                    double discount;
                    try {
                        discount = Double.parseDouble(discountString);
                    } catch (NumberFormatException ex) {
                        return new Result<>(String.format("Product discount format error %1s", discountString), null);
                    }

                    result.put(productId, new PriceStatistics(productId, price, discount));
                }
            }

        } catch (Exception ex) {
            Logger.log(ex.getMessage());
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ex) {
                Logger.log(ex.getMessage());
            }
        }

        return new Result<>(null, result);
    }
}
