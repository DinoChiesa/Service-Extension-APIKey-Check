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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class CacheService {
  private static final Logger logger = Logger.getLogger(CacheService.class.getName());
  private static CacheService instance;

  // A map of predicates to dedicated cache instances.
  private final Map<Predicate<String>, LoadingCache<String, Object>> caches = new HashMap<>();

  // A dedicated thread pool for performing asynchronous cache refreshes.
  private final ExecutorService refreshExecutor =
      Executors.newFixedThreadPool(
          4, // A reasonable number of threads for background tasks
          new ThreadFactoryBuilder()
              .setNameFormat("cache-refresh-%d")
              .setDaemon(true) // Allows the JVM to exit without waiting for these threads
              .build());

  public static synchronized CacheService getInstance() {
    if (instance == null) {
      instance = new CacheService();
    }
    return instance;
  }

  private CacheService() {}

  public Object get(final String key) {
    Optional<LoadingCache<String, Object>> cacheOpt =
        caches.entrySet().stream()
            .filter(entry -> entry.getKey().test(key))
            .map(Map.Entry::getValue)
            .findFirst();

    if (cacheOpt.isPresent()) {
      return cacheOpt.get().get(key);
    }

    logger.warning(String.format("No cache registered for key: %s", key));
    return null;
  }

  public CacheService registerLoader(
      final Predicate<String> test,
      final Function<String, Object> loader,
      final long durationInMinutes)
      throws IllegalStateException {

    CacheLoader<String, Object> cacheLoader =
        new CacheLoader<>() {
          @Override
          public Object load(String key) {
            logger.info(String.format("Synchronously loading data for key: %s", key));
            return loader.apply(key);
          }

          @Override
          public CompletableFuture<Object> reload(String key, Object oldValue) {
            logger.info(String.format("Asynchronously refreshing data for key: %s", key));
            return CompletableFuture.supplyAsync(() -> loader.apply(key), refreshExecutor);
          }
        };

    LoadingCache<String, Object> newCache =
        Caffeine.newBuilder()
            .refreshAfterWrite(durationInMinutes, TimeUnit.MINUTES)
            .expireAfterWrite(durationInMinutes * 2, TimeUnit.MINUTES)
            .maximumSize(100) // Each cache is specialized, so a smaller size is fine.
            .build(cacheLoader);

    caches.put(test, newCache);
    return this;
  }
}
