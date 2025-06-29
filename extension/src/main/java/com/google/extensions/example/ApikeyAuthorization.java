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
import io.envoyproxy.envoy.service.ext_proc.v3.HttpHeaders;
import io.envoyproxy.envoy.service.ext_proc.v3.ProcessingResponse;
import io.envoyproxy.envoy.type.v3.HttpStatus;
import io.envoyproxy.envoy.type.v3.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
  private static final int APIKEYS_TTL_MINUTES = 2;
  private static Map<String, Object> FIXED_KEYS;
  private FetchService fetch = FetchService.getInstance();

  static {
    List<List<String>> keyrows =
        (List<List<String>>)
            List.of(
                List.of("0b919f1d-e113-4d08-976c-a2e2d73f412c", "/status", "GET"),
                List.of("44a39dc0-da72-42f3-8d8d-d6d01378fe4b", "/status", "GET"));
    FIXED_KEYS = ImmutableMap.of("values", keyrows, "loaded", "startup");

    try (InputStream is =
        ApikeyAuthorization.class.getClassLoader().getResourceAsStream("logging.properties")) {
      if (is != null) {
        LogManager.getLogManager().readConfiguration(is);
      }
    } catch (IOException e) {
      System.err.println("Could not load logging.properties file: " + e.getMessage());
    }
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
            "apikeys", (_ignoredKey) -> this.loadApikeys(_ignoredKey), APIKEYS_TTL_MINUTES);
  }

  private Object loadApikeys(String _ignoredKey) {
    logger.info("> loadApikeys");
    String SHEET_ID = System.getenv("SHEET_ID");
    if (SHEET_ID == null) {
      logger.info("No SHEET_ID");
      return FIXED_KEYS;
    }
    try {
      String uri =
          String.format(
              "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s", SHEET_ID, ACL_RANGE);
      logger.info(String.format("fetching %s", uri));
      var map = fetch.get(uri);
      logger.info(String.format("keys loaded from %s", uri));
      map.put("loaded", Instant.now().toString());
      return map;
    } catch (java.lang.Exception exc1) {
      logger.severe("Cannot fetch keys.");
      exc1.printStackTrace();
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
                    logger.info("Authorization header format is invalid.");
                    return null;
                  }
                })
            .orElse(null);

    if (apikey == null) {
      return ApikeyStatus.keyMissing();
    }

    return checkProvidedApiKey(requestHeaders, apikey);
  }

  private static void showMap(Map<String, Object> map) {
    System.out.printf("Map:\n");
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      System.out.printf("%s => (%s) %s\n", key, value.getClass().toString(), value.toString());
      if (key.equals("values")) {
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) value;
        IntStream.range(0, values.size())
            .boxed()
            .forEach(
                ix -> {
                  Object v = values.get(ix);
                  System.out.printf(
                      "   %d => (%s) %s\n", ix, v.getClass().toString(), v.toString());
                });
      }
    }
  }

  private ApikeyStatus checkProvidedApiKey(HttpHeaders headers, String apikey) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) CacheService.getInstance().get("apikeys");
    if (map == null) {
      logger.info("Could not load apikeys from cache.");
      return ApikeyStatus.invalid(apikey);
    }

    String loadedAt = (String) map.get("loaded");
    if (loadedAt != null) {
      if ("startup".equals(loadedAt)) {
        logger.info("API keys were loaded at startup (no expiry).");
      } else {
        Long remainingSeconds = calculateRemainingSeconds(loadedAt);
        if (remainingSeconds != null) {
          if (remainingSeconds < 0) {
            logger.info(
                String.format(
                    "API keys loaded at %s expired %d seconds ago",
                    loadedAt, (0 - remainingSeconds)));
          } else {
            logger.info(
                String.format(
                    "API keys loaded at %s, TTL remaining: %d seconds.",
                    loadedAt, remainingSeconds));
          }
        }
      }
    } else {
      showMap(map);
      logger.info("no key indicating when API keys were loaded.");
    }

    @SuppressWarnings("unchecked")
    List<List<String>> knownkeys = (List<List<String>>) map.get("values");
    if (knownkeys == null) {
      logger.info("No API keys available.");
      return ApikeyStatus.invalid(apikey);
    }

    List<List<String>> matchingKeyEntries =
        knownkeys.stream()
            .filter(keyrow -> !keyrow.isEmpty() && apikey.equals(keyrow.get(0)))
            .collect(Collectors.toList());

    if (matchingKeyEntries.size() == 0) {
      logger.info(String.format("Did not find that API Key (%s).", apikey));
      return ApikeyStatus.invalid(apikey);
    }

    String requestedPath = getHeader(headers, ":path");
    String requestedMethod = getHeader(headers, ":method");
    if (requestedPath == null || requestedMethod == null) {
      logger.warning("Cannot find path and/or method");
      return ApikeyStatus.invalid(apikey);
    }

    boolean isAuthorized =
        matchingKeyEntries.stream()
            .anyMatch(
                keyrow -> {
                  if (keyrow.size() >= 3) {
                    String allowedPath = keyrow.get(1);
                    String allowedMethods = keyrow.get(2);
                    boolean methodMatch =
                        Arrays.stream(allowedMethods.split(","))
                            .map(String::trim)
                            .anyMatch(m -> m.equalsIgnoreCase(requestedMethod));
                    String pathRegex = "^" + allowedPath.replace("*", "[^/]+") + "$";
                    return requestedPath.matches(pathRegex) && methodMatch;
                  }
                  return false;
                });

    if (isAuthorized) {
      return ApikeyStatus.valid(apikey);
    }

    logger.info(
        String.format(
            "API Key is valid, but not authorized for %s %s", requestedMethod, requestedPath));
    return ApikeyStatus.noMatch(apikey);
  }

  private static Long calculateRemainingSeconds(String loadedAt) {
    try {
      Instant loadedInstant = Instant.parse(loadedAt);
      int ttlMinutes = APIKEYS_TTL_MINUTES;
      Instant expiryTime = loadedInstant.plus(ttlMinutes, ChronoUnit.MINUTES);
      return Duration.between(Instant.now(), expiryTime).toSeconds();
    } catch (java.time.format.DateTimeParseException e) {
      logger.warning(String.format("Could not parse load time for API keys: '%s'", loadedAt));
      return null;
    }
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
    headers.getHeaders().getHeadersList().stream()
        .forEach(
            header -> {
              String val = new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8);
              logger.info(
                  String.format(
                      "Header: %s = %s", header.getKey(), maybeMaskHeader(header.getKey(), val)));
            });
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
      logger.info("Valid API key, request allowed.");
      return;
    }

    logger.info(String.format("API key check negative: %s", apikeyStatus.getMessage()));

    StatusCode statusCode = StatusCode.Forbidden;
    ImmutableMap<String, String> responseHeadersToAdd = null;
    if (apikeyStatus.isKeyMissing()) {
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
    logger.info(
        String.format(
            "ApiKeyAuthorization version:%s build-time:%s",
            ju.getAttribute("Project-Version"), ju.getAttribute("Build-Time")));

    server.start();
    server.blockUntilShutdown();
  }
}
