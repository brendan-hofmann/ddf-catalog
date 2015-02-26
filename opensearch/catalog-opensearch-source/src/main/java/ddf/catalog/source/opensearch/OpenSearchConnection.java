/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package ddf.catalog.source.opensearch;

import ddf.catalog.filter.FilterAdapter;
import ddf.catalog.operation.Query;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.security.Subject;
import ddf.security.service.SecurityServiceException;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.codice.ddf.cxf.SecureCxfClientFactory;
import org.codice.ddf.endpoints.OpenSearch;
import org.codice.ddf.endpoints.rest.RESTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

/**
 * This class wraps the CXF JAXRS code to make it easier to use and also easier to test. Most of
 * the CXF code uses static methods to construct the web clients, which is inherently difficult to
 * mock up when testing.
 */
public class OpenSearchConnection {

    private static final transient Logger LOGGER = LoggerFactory
            .getLogger(OpenSearchConnection.class);

    protected SecureCxfClientFactory<OpenSearch> openSearchClient;

    private FilterAdapter filterAdapter;

    private String username;

    private String password;

    /**
     * Default Constructor
     *
     * @param endpointUrl   - OpenSearch URL to connect to
     * @param filterAdapter - adapter to translate between DDF REST and OpenSearch
     * @param username      - Basic Auth user name
     * @param password      - Basic Auth password
     */
    public OpenSearchConnection(String endpointUrl, FilterAdapter filterAdapter, String username,
            String password) throws SecurityServiceException {
        this.filterAdapter = filterAdapter;
        this.username = username;
        this.password = password;
        openSearchClient = new SecureCxfClientFactory<>(endpointUrl, OpenSearch.class, null, null,
                true);
    }

    /**
     * Generates a DDF REST URL from an OpenSearch URL
     *
     * @param query
     * @param endpointUrl
     * @return URL in String format
     */
    private String createRestUrl(Query query, String endpointUrl, boolean retrieveResource) {

        String url = null;
        RestFilterDelegate delegate = null;
        RestUrl restUrl = newRestUrl(endpointUrl);

        if (restUrl != null) {
            restUrl.setRetrieveResource(retrieveResource);
            delegate = new RestFilterDelegate(restUrl);
        }

        if (delegate != null) {
            try {
                filterAdapter.adapt(query, delegate);
                url = delegate.getRestUrl().buildUrl();
            } catch (UnsupportedQueryException e) {
                LOGGER.debug("Not a REST request.", e);
            }

        }

        return url;
    }

    /**
     * Creates a new RestUrl object based on an OpenSearch URL
     *
     * @param url
     * @return RestUrl object for a DDF REST endpoint
     */
    private RestUrl newRestUrl(String url) {
        RestUrl restUrl = null;
        try {
            restUrl = RestUrl.newInstance(url);
            restUrl.setRetrieveResource(true);
        } catch (MalformedURLException e) {
            LOGGER.info("Bad url given for remote source", e);
        } catch (URISyntaxException e) {
            LOGGER.info("Bad url given for remote source", e);
        }
        return restUrl;
    }

    /**
     * Returns the OpenSearch {@link org.apache.cxf.jaxrs.client.WebClient}
     *
     * @return {@link org.apache.cxf.jaxrs.client.WebClient}
     */
    public WebClient getOpenSearchWebClient(Subject subject) throws SecurityServiceException {
        if (subject == null) {
            return openSearchClient.getWebClientForBasicAuth(username, password);
        } else {
            return openSearchClient.getWebClientForSubject(subject);
        }
    }

    /**
     * Creates a new DDF REST {@link org.apache.cxf.jaxrs.client.Client} based on an OpenSearch
     * String URL.
     *
     * @param url              - OpenSearch URL
     * @param query            - Query to be performed
     * @param metacardId       - MetacardId to search for
     * @param retrieveResource - true if this is a resource request
     * @return {@link org.apache.cxf.jaxrs.client.Client}
     */
    public WebClient newRestWebClient(String url, Query query, String metacardId,
            boolean retrieveResource, Subject subject) {
        if (query != null) {
            url = createRestUrl(query, url, retrieveResource);
        } else {
            RestUrl restUrl = newRestUrl(url);

            if (restUrl != null) {
                if (StringUtils.isNotEmpty(metacardId)) {
                    restUrl.setId(metacardId);
                }
                restUrl.setRetrieveResource(retrieveResource);
                url = restUrl.buildUrl();
            }
        }
        WebClient tmp = null;
        if (url != null) {
            try {
                if (subject != null) {
                    tmp = new SecureCxfClientFactory<>(url, RESTService.class, null, null, true)
                            .getWebClientForSubject(subject);
                } else {
                    tmp = new SecureCxfClientFactory<>(url, RESTService.class, null, null, true)
                            .getWebClientForBasicAuth(username, password);
                }
            } catch (SecurityServiceException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp;
    }
}
