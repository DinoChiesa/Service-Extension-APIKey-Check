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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example callout server that checks an API Key provided in the Authorization header.
 *
 * <p>This class demonstrates how to handle a request header callout and check for a valid APIkey.
 */
public class ApikeyAuthorization extends ServiceCallout {

  private static final Logger logger = LoggerFactory.getLogger(ApikeyAuthorization.class);

  private static final List<String> APIKEYS =
      List.of("44a39dc0-da72-42f3-8d8d-d6d01378fe4b", "0b919f1d-e113-4d08-976c-a2e2d73f412c");

  // Constructor that calls the superclass constructor
  public ApikeyAuthorization(ApikeyAuthorization.Builder builder) {
    super(builder);
  }

  // Builder specific to ApikeyAuthorization
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

  static class ApikeyStatus {
    private boolean _valid;
    private String _message;
    public static final ApikeyStatus MissingApiKey = new ApikeyStatus(false, "API Key not present");
    public static final ApikeyStatus InvalidApiKey = new ApikeyStatus(false, "invalid API Key");
    public static final ApikeyStatus ValidApiKey = new ApikeyStatus(true, "valid API Key");

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

  private ApikeyStatus verifyApiKey(HttpHeaders requestHeaders) {

    requestHeaders.getHeaders().getHeadersList().stream()
        .forEach(
            header -> {
              logger.info("Header: {} = {}", header.getKey(), header.getRawValue());
            });

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
      return ApikeyStatus.MissingApiKey;
    }

    if (APIKEYS.contains(apikey)) {
      return ApikeyStatus.ValidApiKey;
    } else {
      return ApikeyStatus.InvalidApiKey;
    }
  }

  private static String getHeader(HttpHeaders headers, String headerName) {
    return headers.getHeaders().getHeadersList().stream()
        .filter(header -> headerName.equalsIgnoreCase(header.getKey()))
        .map(header -> new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8))
        .findFirst()
        .orElse(null);
  }

  private void logHeaders(HttpHeaders headers) {
    try {
      logger.info(">>logHeaders");
      headers.getHeaders().getHeadersList().stream()
          .forEach(
              header -> {
                String val = "-unset-";
                try {
                  val = new String(header.getRawValue().toByteArray(), StandardCharsets.UTF_8);
                } catch (java.lang.Exception exc1) {
                  logger.info("Exception getting header value:" + exc1.toString());
                }
                logger.info("Header: {} = {}", header.getKey(), val);
              });
      logger.info("<<logHeaders");
    } catch (java.lang.Exception exc1) {
      logger.info("logHeaders Exception:" + exc1.toString());
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
    logHeaders(headers);

    ApikeyStatus apikeyStatus = verifyApiKey(headers);

    if (apikeyStatus.isValid()) {
      // API key is valid, do nothing and let the request proceed.
      logger.info("Valid API key, request allowed.");
      return;
    }

    // API key is invalid or missing, send an immediate error response.
    logger.info("API key check failed: {}", apikeyStatus.getMessage());

    ImmutableMap<String, String> authnHeaders =
        ImmutableMap.of("WWW-Authenticate", "APIKey realm=\"example.com\"");

    // Prepare the status for 401 Unauthorized
    HttpStatus status = HttpStatus.newBuilder().setCode(StatusCode.Unauthorized).build();

    // Modify the ImmediateResponse.Builder to send a 401 response
    ServiceCalloutTools.buildImmediateResponse(
        processingResponseBuilder.getImmediateResponseBuilder(),
        status,
        authnHeaders, // No headers to add
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
    server.start();
    server.blockUntilShutdown();
  }
}
