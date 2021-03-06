/*
 * Copyright (c) 2018 - present Fidesmo AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.fidesmo.fdsm;

import apdu4j.HexUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public class FidesmoApiClient {
    public static final String APIv2 = "https://api.fidesmo.com/v2/";

    public static final String APPS_URL = "apps";
    public static final String APP_INFO_URL = "apps/%s";
    public static final String APP_SERVICES_URL = "apps/%s/services";

    public static final String SERVICE_URL = "apps/%s/services/%s";
    public static final String SERVICE_RECIPE_URL = "apps/%s/services/%s/recipe";
    public static final String RECIPE_SERVICES_URL = "apps/%s/recipe-services";

    public static final String ELF_URL = "executableLoadFiles";
    public static final String ELF_ID_URL = "executableLoadFiles/%s";

    public static final String SERVICE_DELIVER_URL = "service/deliver";
    public static final String SERVICE_FETCH_URL = "service/fetch";

    public static final String CONNECTOR_URL = "connector/json";
    public static final String CONNECTOR_ERROR_URL = "connector/error";


    private boolean restdebug = false; // RPC debug
    private final CloseableHttpClient http;
    private final HttpClientContext context = HttpClientContext.create();
    protected final String appId;
    protected final String appKey;
    private final String apiurl;

    static DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
    static ObjectMapper mapper = new ObjectMapper();

    static {
        // Configure our pretty printer
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
    }

    public FidesmoApiClient() {
        this(null, null);
    }

    public FidesmoApiClient(String appId, String appKey) {
        if (appId != null && appKey != null) {
            if (HexUtils.hex2bin(appId).length != 4)
                throw new IllegalArgumentException("appId must be 4 bytes long (8 hex characters)");
            if (HexUtils.hex2bin(appKey).length != 16)
                throw new IllegalArgumentException("appKey must be 16 bytes long (32 hex characters)");
        }
        if (System.getenv().containsKey("FIDESMO_API_URL")) {
            String check;
            try {
                check = new URL(System.getenv("FIDESMO_API_URL")).toString();
            } catch (MalformedURLException e) {
                // Silently ignore malformed URL-s
                check = APIv2;
            }
            this.apiurl = check;
        } else {
            this.apiurl = APIv2;
        }
        this.http = HttpClientBuilder.create().useSystemProperties().setUserAgent("fdsm/" + getVersion()).build();
        this.appId = appId;
        this.appKey = appKey;
    }

    public static boolean checkAppId(String appId) {
        try {
            return HexUtils.hex2bin(appId).length == 4;
        } catch (IllegalArgumentException e) {
            // Pass through
        }
        return false;
    }

    CloseableHttpResponse transmit(HttpRequestBase request) throws IOException {
        if (appId != null && appKey != null) {
            request.setHeader("app_id", appId);
            request.setHeader("app_key", appKey);
        }
        if (restdebug) {
            System.out.println(request.getMethod() + ": " + request.getURI());
        }

        CloseableHttpResponse response = http.execute(request, context);
        int responsecode = response.getStatusLine().getStatusCode();
        if (responsecode < 200 || responsecode > 299) {
            String message = response.getStatusLine() + "\n" + IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            response.close();
            throw new HttpResponseException(responsecode, message);
        }
        return response;
    }

    JsonNode rpc(URI uri) throws IOException {
        return rpc(uri, null);
    }

    JsonNode rpc(URI uri, JsonNode request) throws IOException {
        HttpRequestBase req;
        if (request != null) {
            HttpPost post = new HttpPost(uri);
            post.setEntity(new ByteArrayEntity(mapper.writeValueAsBytes(request)));
            req = post;
            if (restdebug) {
                System.out.println("POST: " + uri);
                System.out.println(mapper.writer(printer).writeValueAsString(request));
            }
        } else {
            HttpGet get = new HttpGet(uri);
            req = get;
            if (restdebug) {
                System.out.println("GET: " + uri);
            }
        }

        req.setHeader("Accept", ContentType.APPLICATION_JSON.toString());
        req.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());

        try (CloseableHttpResponse response = transmit(req)) {
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            if (restdebug) {
                System.out.println("RECV:");
                System.out.println(mapper.writer(printer).writeValueAsString(json));
            }
            return json;
        }
    }

    public URI getURI(String template, String... args) {
        try {
            return new URI(String.format(apiurl + template, args));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid stuff: " + e.getMessage(), e);
        }
    }

    public void setTrace(boolean b) {
        restdebug = b;
    }

    public static String getVersion() {
        try (InputStream versionfile = FidesmoApiClient.class.getResourceAsStream("version.txt")) {
            String version = "unknown-development";
            if (versionfile != null) {
                try (BufferedReader vinfo = new BufferedReader(new InputStreamReader(versionfile, StandardCharsets.US_ASCII))) {
                    version = vinfo.readLine();
                }
            }
            return version;
        } catch (IOException e) {
            return "unknown-error";
        }
    }

    // Prefer English if system locale is not present
    // to convert a possible multilanguage node to a string
    public static String lamei18n(JsonNode n) {
        // For missing values
        if (n == null)
            return "";
        if (n.size() > 0) {
            Map<String, Object> langs = mapper.convertValue(n, new TypeReference<Map<String, Object>>() {});
            Map.Entry<String, Object> first = langs.entrySet().iterator().next();
            return langs.getOrDefault(Locale.getDefault().getLanguage(), langs.getOrDefault("en", first.getValue())).toString();
        } else {
            return n.asText();
        }
    }
}
