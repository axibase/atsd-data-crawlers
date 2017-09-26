package com.axibase.crawler.urlsearching;

import com.axibase.crawler.common.Logger;
import com.axibase.crawler.data.ProductUrl;
import com.axibase.crawler.common.Result;
import com.axibase.crawler.orders.OrderInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UrlSearcher {
    public List<ProductUrl> searchUrls(Collection<OrderInfo> orders)
    {
        ProductSearcher searcher = new ProductSearcher();

        Result<ArrayList<Integer>> storeIdsResult = searcher.searchStoreIds();
        if (storeIdsResult.errorText != null) {
            Logger.log(storeIdsResult.errorText);
            return null;
        }

        int productIndex = 0;
        int productsFound = 0;
        ArrayList<ProductUrl> productUrls = new ArrayList<>(orders.size());

        for (OrderInfo order : orders) {
            productIndex++;
            Logger.log("Searching product %1s/%2s", productIndex, orders.size());

            Result<String> searchResult = null;
            for (Integer storeId : storeIdsResult.result) {
                searchResult = searcher.searchProductUrl(storeId, order.productName);

                if (searchResult.errorText == null && searchResult.result != null && !searchResult.result.startsWith("wcs/")) {
                    break;
                }
            }

            if (searchResult == null) {
                Logger.log("Searching product %1s error", order.productName);
                productUrls.add(new ProductUrl(order.productId, order.productName, null));
                continue;
            }

            if (searchResult.errorText != null) {
                Logger.log("Searching product %1s error: %2s", order.productName, searchResult.errorText);
                productUrls.add(new ProductUrl(order.productId, order.productName, null));
                continue;
            }

            productUrls.add(new ProductUrl(order.productId, order.productName, searchResult.result));
            productsFound++;
        }

        Logger.log("Finished. Urls found for %1s products", productsFound);

        return productUrls;
    }
}