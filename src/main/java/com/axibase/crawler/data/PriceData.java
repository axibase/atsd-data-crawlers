package com.axibase.crawler.data;

public class PriceData {
    public final int id;

    public final String zone;

    public final Double price;

    public final Double discount;

    public PriceData(
            int id,
            String zone,
            Double price,
            Double discount) {
        this.id = id;
        this.zone = zone;
        this.price = price;
        this.discount = discount;
    }
}
