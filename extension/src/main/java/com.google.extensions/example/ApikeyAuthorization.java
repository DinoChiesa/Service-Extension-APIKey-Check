/*
 * Copyright (c) 2024, 2025 Google, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.extensions.example;

import com.google.common.collect.ImmutableMap;
import com.google.extensions.service.ServiceCallout;
import com.google.extensions.service.ServiceCalloutTools;
import io.envoyproxy.envoy.config.core.v3.HeaderValue;
import io.envoyproxy.envoy.service.ext_proc.v3.HttpHeaders;
import io.envoyproxy.envoy.service.ext_proc.v3.ProcessingResponse;
import io.envoyproxy.envoy.type.v3.HttpStatus;
import io.envoyproxy.envoy.type.v3.StatusCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import utils.JarUtils;

/**
 * Example callout server that checks an API Key provided in the Authorization header.
 *
 * <p>This class demonstrates an authorization extension callout that checks for a valid APIkey in
 * the request header.
 */
public class ApikeyAuthorization extends ServiceCallout {

  private static final Logger logger = Logger.getLogger(ApikeyAuthorization.class.getName());
  private static final String ACL_RANGE = "Keys!A2:C102";
  private static Map<String, Object> FIXED_KEYS;

  static {
    List<List<String>> keyrows =
        (List<List<String>>)
            List.of(
                List.of("0b919f1d-e113-4d08-976c-a2e2d73f412c", "/status", "GET"),
                List.of("44a39dc0-da72-42f3-8d8d-d6d01378fe4b", "/status", "GET"));
    FIXED_KEYS = ImmutableMap.of("values", keyrows, "loaded", "startup");
  }

  private boolean verbose = false;

  public static class Builder extends ServiceCallout.Builder<ApikeyAuthorization.Builder> {

    @Override
    public ApikeyAuthorization build() {
      return new ApikeyAuthorization(this);
    }

    @Override
    protected ApikeyAuthorization.Builder self() {
      return this;
    }
  }

  public ApikeyAuthorization(ApikeyAuthorization.Builder builder) {
    super(builder);
    verbose = "true".equalsIgnoreCase(System.getenv("VERBOSE"));

    CacheService.getInstance()
        .registerLoader(
            (key) -> key.equals("apikeys"), (_ignoredKey) -> this.loadApikeys(_ignoredKey));
  }

  static class ApikeyStatus {
    private boolean _valid;
    private String _message;
    public static final ApikeyStatus MissingApiKey = new ApikeyStatus(false, "API Key not present");
    public static final ApikeyStatus InvalidApiKey = new ApikeyStatus(false, "Invalid API Key");
    public static final ApikeyStatus ValidApiKey = new ApikeyStatus(true, "Valid API Key");
    public static final ApikeyStatus NoMatchingOperation =
        new ApikeyStatus(false, "No matching operation found");

    public ApikeyStatus(boolean validity, String message) {
      _valid = validity;
      _message = message;
    }

    public boolean isValid() {
      return _valid;
    }

    public String getMessage() {
      return _message;
    }
  }

  private Object loadApikeys(String _ignoredKey) {
    logger.log(Level.INFO, "> loadApikeys");
    String SHEET_ID = System.getenv("SHEET_ID");
    if (SHEET_ID == null) {
      logger.log(Level.INFO, "No SHEET_ID");
      return FIXED_KEYS;
    }
    try {
      String uri =
          String.format(
              "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s", SHEET_ID, ACL_RANGE);
      logger.log(Level.INFO, String.format("fetching %s", uri));
      var fetch = new FetchService();
      var map = fetch.get(uri);
      map.put("loaded", Instant.now().toString());
      return map;
    } catch (java.lang.Exception exc1) {
      logger.log(Level.SEVERE, "Cannot fetch keys.", exc1);
    }
    return FIXED_KEYS;
  }

  private ApikeyStatus verifyApiKey(HttpHeaders requestHeaders) {
    if (verbose) {
      logHeaders(requestHeaders);
    }
    String apikey =
        requestHeaders.getHeaders().getHeadersList().stream()
            .filter(header -> "Authorization".equalsIgnoreCase(header.getKey()))
            .map(header -> new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8))
            .findFirst()
            .map(
                authHeader -> {
                  String[] parts = authHeader.split(" ");
                  if (parts.length == 2 && "APIKEY".equalsIgnoreCase(parts[0])) {
                    return parts[1];
                  } else {
                    logger.log(Level.INFO, "Authorization header format is invalid.");
                    return null;
                  }
                })
            .orElse(null);

    if (apikey == null) {
      return ApikeyStatus.MissingApiKey;
    }

    return checkProvidedApiKey(requestHeaders, apikey);
  }

  // private static void showMap(Map<String, Object> map) {
  //   for (Map.Entry<String, Object> entry : map.entrySet()) {
  //     String key = entry.getKey();
  //     Object value = entry.getValue();
  //     System.out.printf("%s => (%s) %s\n", key, value.getClass().toString(), value.toString());
  //     if (key.equals("values")) {
  //       @SuppressWarnings("unchecked")
  //       List<Object> values = (List<Object>) value;
  //       IntStream.range(0, values.size())
  //           .boxed()
  //           .forEach(
  //               ix -> {
  //                 Object v = values.get(ix);
  //                 System.out.printf(
  //                     "   %d => (%s) %s\n", ix, v.getClass().toString(), v.toString());
  //               });
  //     }
  //   }
  // }

  private ApikeyStatus checkProvidedApiKey(HttpHeaders headers, String apikey) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) CacheService.getInstance().get("apikeys");
    if (map == null) {
      logger.log(Level.INFO, "Could not load apikeys from cache.");
      return ApikeyStatus.InvalidApiKey;
    }
    // showMap(map);
    logger.log(
        Level.INFO, String.format("API keys were loaded at %s.", (String) map.get("loaded")));
    @SuppressWarnings("unchecked")
    List<List<String>> knownkeys = (List<List<String>>) map.get("values");
    if (knownkeys == null) {
      logger.log(Level.INFO, "No API keys available.");
      return ApikeyStatus.InvalidApiKey;
    }

    List<List<String>> matchingKeyEntries =
        knownkeys.stream()
            .filter(keyrow -> !keyrow.isEmpty() && apikey.equals(keyrow.get(0)))
            .collect(Collectors.toList());

    if (matchingKeyEntries.size() == 0) {
      logger.log(Level.INFO, String.format("Did not find that API Key (%s).", apikey));
      return ApikeyStatus.InvalidApiKey;
    }

    String requestedPath = getHeader(headers, ":path");
    String requestedMethod = getHeader(headers, ":method");
    boolean isAuthorized =
        matchingKeyEntries.stream()
            .anyMatch(
                keyrow -> {
                  if (keyrow.size() >= 3) {
                    String allowedPath = keyrow.get(1);
                    String allowedMethod = keyrow.get(2);
                    String pathRegex = "^" + allowedPath.replace("*", "[^/]+") + "$";
                    return requestedPath.matches(pathRegex)
                        && allowedMethod.equals(requestedMethod);
                  }
                  return false;
                });

    if (isAuthorized) {
      return ApikeyStatus.ValidApiKey;
    }

    logger.log(
        Level.INFO,
        String.format(
            "API Key is valid, but not authorized for path=%s, method=%s",
            requestedPath, requestedMethod));
    return ApikeyStatus.NoMatchingOperation;
  }

  private static String getHeader(HttpHeaders headers, String headerName) {
    return headers.getHeaders().getHeadersList().stream()
        .filter(header -> headerName.equalsIgnoreCase(header.getKey()))
        .map(header -> new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8))
        .findFirst()
        .orElse(null);
  }

  private static String maybeMaskHeader(String key, String value) {
    // if ("Authorization".equalsIgnoreCase(key)) {
    //   String[] parts = value.split(" ");
    //   if (parts.length >= 2) {
    //     value = parts[0] + " **********";
    //   } else {
    //     value = "**********";
    //   }
    // }
    return value;
  }

  private static void logHeaders(HttpHeaders headers) {
    for (HeaderValue header : headers.getHeaders().getHeadersList()) {
      String val = new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8);
      logger.log(
          Level.INFO,
          String.format(
              "Header: %s = %s", header.getKey(), maybeMaskHeader(header.getKey(), val)));
    }
  }

  /**
   * Handles request headers .
   *
   * <p>This method checks for a valid API key. If the key is valid, the request is allowed to
   * proceed. If the key is missing or invalid, an immediate `401 Unauthorized` response is sent.
   *
   * @param processingResponseBuilder the {@link ProcessingResponse.Builder} used to construct the
   *     immediate response.
   * @param headers the {@link HttpHeaders} representing the incoming request headers (not
   *     modified).
   */
  @Override
  public void onRequestHeaders(
      ProcessingResponse.Builder processingResponseBuilder, HttpHeaders headers) {
    ApikeyStatus apikeyStatus = verifyApiKey(headers);

    if (apikeyStatus.isValid()) {
      logger.log(Level.INFO, "Valid API key, request allowed.");
      return;
    }

    logger.log(Level.INFO, String.format("API key check negative: %s", apikeyStatus.getMessage()));

    StatusCode statusCode = StatusCode.Forbidden;
    ImmutableMap<String, String> responseHeadersToAdd = null;
    if (apikeyStatus.equals(ApikeyStatus.MissingApiKey)) {
      responseHeadersToAdd = ImmutableMap.of("WWW-Authenticate", "APIKey realm=\"example.com\"");
      statusCode = StatusCode.Unauthorized;
    }

    HttpStatus status = HttpStatus.newBuilder().setCode(statusCode).build();
    ServiceCalloutTools.buildImmediateResponse(
        processingResponseBuilder.getImmediateResponseBuilder(),
        status,
        responseHeadersToAdd,
        null, // No headers to remove
        apikeyStatus.getMessage() + "\n");
  }

  /**
   * Main method to start the gRPC callout server with a custom configuration using the {@link
   * ServiceCallout.Builder}.
   *
   * <p>This method initializes the server with default or custom configurations, starts the server,
   * and keeps it running until manually terminated. The server processes incoming gRPC requests for
   * HTTP manipulations.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * ServiceCallout.Builder builder = new ServiceCallout.Builder()
   *     .setIp("111.222.333.444")       // Customize IP
   *     .setPort(8443)                  // Set the port for secure communication
   *     .setEnablePlainTextPort(true)   // Enable an plaintext communication port
   *     .setServerThreadCount(4);       // Set the number of server threads
   * }</pre>
   *
   * @param args Command-line arguments, not used in this implementation.
   * @throws Exception If an error occurs during server startup or shutdown.
   */
  public static void main(String[] args) throws Exception {
    ApikeyAuthorization server = new ApikeyAuthorization.Builder().build();
    var ju = new JarUtils();
    logger.log(
        Level.INFO,
        String.format(
            "ApiKeyAuthorization version:%s build-time:%s",
            ju.getAttribute("Project-Version"), ju.getAttribute("Build-Time")));

    server.start();
    server.blockUntilShutdown();
  }
}
