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

public class ApikeyStatus {
  private Result _result = Result.Unset;
  private String _apikey;

  enum Result {
    KeyMissing,
    InvalidNotFound,
    FoundNoMatch,
    Valid,
    Unset
  };

  private ApikeyStatus(String apikey, Result result) {
    _apikey = apikey;
    _result = result;
  }

  public boolean isValid() {
    return _result == Result.Valid;
  }

  public boolean isKeyMissing() {
    return _result == Result.KeyMissing;
  }

  public boolean isResult(Result result) {
    return _result == result;
  }

  public String getMessage() {
    switch (_result) {
      case KeyMissing:
        return "API Key not present";
      case InvalidNotFound:
        return "Invalid API Key";
      case FoundNoMatch:
        return "No matching operation found";
      case Valid:
        return "Valid API Key";
      case Unset:
      default:
        return "No status";
    }
  }

  public static ApikeyStatus.Builder builder() {
    return new ApikeyStatus.Builder();
  }

  public static class Builder {
    private Result _result = Result.Unset;
    private String _apikey;
    private boolean _final;

    private Builder() {}

    public static ApikeyStatus.Builder forKey(String apikey) {
      var builder = new ApikeyStatus.Builder();
      builder._apikey = apikey;
      return builder;
    }

    private ApikeyStatus build() {
      if (_final) {
        throw new RuntimeException("already final");
      }
      _final = true;
      return new ApikeyStatus(_apikey, _result);
    }

    public ApikeyStatus keyMissing() {
      _result = Result.KeyMissing;
      return this.build();
    }

    public ApikeyStatus invalid() {
      _result = Result.InvalidNotFound;
      return this.build();
    }

    public ApikeyStatus noMatch() {
      _result = Result.FoundNoMatch;
      return this.build();
    }

    public ApikeyStatus valid() {
      _result = Result.Valid;
      return this.build();
    }
  }
}
