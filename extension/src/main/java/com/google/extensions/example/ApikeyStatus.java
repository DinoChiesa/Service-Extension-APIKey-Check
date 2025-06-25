/*
 * Copyright (c) 2025 Google, LLC.
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

public record ApikeyStatus(String apikey, Result result) {

  public enum Result {
    KeyMissing,
    InvalidNotFound,
    FoundNoMatch,
    Valid,
    Unset
  }

  public boolean isValid() {
    return result == Result.Valid;
  }

  public boolean isKeyMissing() {
    return result == Result.KeyMissing;
  }

  public String getMessage() {
    return switch (result) {
      case KeyMissing -> "API Key not present";
      case InvalidNotFound -> "Invalid API Key";
      case FoundNoMatch -> "No matching operation found";
      case Valid -> "Valid API Key";
      default -> "No status"; // Unset
    };
  }

  public static ApikeyStatus keyMissing() {
    return new ApikeyStatus(null, Result.KeyMissing);
  }

  public static ApikeyStatus invalid(String apikey) {
    return new ApikeyStatus(apikey, Result.InvalidNotFound);
  }

  public static ApikeyStatus noMatch(String apikey) {
    return new ApikeyStatus(apikey, Result.FoundNoMatch);
  }

  public static ApikeyStatus valid(String apikey) {
    return new ApikeyStatus(apikey, Result.Valid);
  }
}
