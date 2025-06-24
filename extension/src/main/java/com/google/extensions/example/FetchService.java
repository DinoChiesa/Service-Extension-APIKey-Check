// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.extensions.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FetchService {
  private static final Logger logger = Logger.getLogger(FetchService.class.getName());
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Type mapType = new TypeToken<HashMap<String, Object>>() {}.getType();
  private static final int TOKEN_TTL_MINUTES = 30;

  public FetchService() throws IOException, InterruptedException, URISyntaxException {
    CacheService.getInstance()
        .registerLoader(
            (key) -> key.endsWith("gcptoken"),
            (_ignoredKey) -> this.loadGcpAccessToken(_ignoredKey),
            TOKEN_TTL_MINUTES);
  }

  public static boolean isRunningInCloud() {
    // Google Cloud Run automatically sets the K_SERVICE environment variable.
    String kService = System.getenv("K_SERVICE");
    return kService != null && !kService.isEmpty();
  }

  /**
   * Cache loader function to retrieve a GCP access token. Checks if running in Cloud Run to
   * determine the token retrieval method.
   *
   * @param ignoredKey The cache key (ignored in this implementation).
   * @return The access token as a String, or null if an error occurs.
   */
  private Object loadGcpAccessToken(String _ignoredKey) {
    if (isRunningInCloud()) {
      logger.log(Level.INFO, "Running in Cloud Run, fetching token from metadata server...");
      String metadataUrl =
          "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
      try {
        String responseBody = _fetch(metadataUrl, "GET", Map.of("Metadata-Flavor", "Google"), null);
        if (responseBody != null) {
          Map<String, Object> tokenResponse = gson.fromJson(responseBody, mapType);
          if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
            String accessToken = (String) tokenResponse.get("access_token");
            logger.log(Level.INFO, "Successfully fetched token from metadata server.");
            return accessToken;
          }
        }
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Unexpected error fetching/parsing token from metadata server", e);
      }
      return null;
    }
    logger.log(Level.INFO, "Not running in Cloud Run, using gcloud for token...");
    String project = System.getenv("PROJECT_ID");
    if (project == null) {
      throw new RuntimeException("No PROJECT_ID set... cannot continue.");
    }
    String sa_email = System.getenv("SA_EMAIL");
    if (sa_email == null) {
      throw new RuntimeException("No SA_EMAIL set... cannot continue.");
    }
    return executeCommand(
        "gcloud",
        "auth",
        "print-access-token",
        "--impersonate-service-account",
        sa_email,
        "--project",
        project,
        "--scopes",
        "https://www.googleapis.com/auth/spreadsheets.readonly",
        "--quiet");
  }

  private static String _fetch(
      String uri, String method, Map<String, String> requestHeaders, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    logger.log(Level.FINE, String.format("*** fetch [%s %s]...", method, uri));

    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(new URI(uri));
    if (requestHeaders != null) {
      for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }
    }

    if ("GET".equals(method)) {
      builder = builder.GET();
    } else if ("POST".equals(method)) {
      builder =
          builder
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)));
    } else if ("DELETE".equals(method)) {
      builder = builder.DELETE();
    } else {
      throw new RuntimeException("HTTP method not supported: " + method);
    }

    HttpRequest request = builder.build();
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    HttpHeaders responseHeaders = response.headers();
    logger.log(Level.FINE, String.format("Response headers:\n%s", responseHeaders.toString()));
    String body = response.body();
    logger.log(Level.FINE, String.format("\n\n=>\n%s", body));
    return body;
  }

  public Map<String, Object> fetch(String uri, String method, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    String accessToken = (String) CacheService.getInstance().get("gcptoken");
    String stringResult =
        _fetch(uri, method, Map.of("Authorization", "Bearer " + accessToken), payload);
    Map<String, Object> json = gson.fromJson(stringResult, mapType);
    return json;
  }

  public Map<String, Object> get(String path)
      throws URISyntaxException, IOException, InterruptedException {
    return fetch(path, "GET", null);
  }

  public Map<String, Object> post(String path, Map<String, Object> payload)
      throws URISyntaxException, IOException, InterruptedException {
    return fetch(path, "POST", payload);
  }

  /**
   * Executes an external command and returns its standard output as a String. This method blocks
   * until the command completes.
   *
   * @param command The command and its arguments to execute (e.g., "gcloud", "auth",
   *     "print-identity-token").
   * @return The standard output of the command, trimmed of leading/trailing whitespace.
   * @throws IOException If an I/O error occurs during process creation or stream reading.
   * @throws InterruptedException If the current thread is interrupted while waiting for the process
   *     to complete.
   * @throws RuntimeException If the command execution fails (non-zero exit code).
   */
  private static String executeCommand(String... command) { // Changed to static method
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      logger.log(Level.INFO, String.format("Executing command: %s", String.join(" ", command)));

      Function<InputStream, String> slurp =
          inputStream -> {
            try {
              StringBuilder output = new StringBuilder();
              // Use try-with-resources to ensure the reader is closed automatically
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  output.append(line).append(System.lineSeparator()); // Append lines as they come
                }
              }
              return output.toString().trim();
            } catch (java.lang.Exception exc1) {
              logger.log(
                  Level.SEVERE, String.format("Exception in slurp: %s", exc1.toString()), exc1);
              return null;
            }
          };

      Process process = processBuilder.start();
      String output = slurp.apply(process.getInputStream());
      boolean finished = process.waitFor(60, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new RuntimeException("Command timed out: " + String.join(" ", command));
      }
      int exitCode = process.exitValue();

      if (exitCode != 0) {
        // Read the error stream for more details if the command failed
        String errorOutput = slurp.apply(process.getErrorStream());
        throw new RuntimeException(
            String.format(
                "Command failed with exit code %d. [%s] error:%s",
                exitCode, String.join(" ", command), errorOutput));
      }

      return output;
    } catch (java.lang.Exception exc1) {
      logger.log(
          Level.SEVERE, String.format("Exception executing command: %s", exc1.toString()), exc1);
    }
    return null;
  }
}
