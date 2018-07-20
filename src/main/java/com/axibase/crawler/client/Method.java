package com.axibase.crawler.client;


import lombok.Getter;

import javax.ws.rs.client.WebTarget;

@Getter
public abstract class Method<T> {
    private final WebTarget resource;

    Method(final String path, final WebTarget baseResource) {
        this.resource = baseResource.path(path);
    }

    /**
     * Execute method.
     * @return Method entity.
     */
    public abstract T execute();
}
