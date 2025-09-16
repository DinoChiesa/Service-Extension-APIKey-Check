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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheService {
  private static final Logger logger = Logger.getLogger(CacheService.class.getName());

  private final Map<String, CacheEntry> caches = new ConcurrentHashMap<>();
  private final ExecutorService refreshExecutor =
      Executors.newFixedThreadPool(
          4, new ThreadFactoryBuilder().setNameFormat("cache-refresh-%d").setDaemon(true).build());

  private static class CacheEntry {
    volatile Object value;
    volatile Instant expiryTime;
    final String key;
    final Function<String, Object> loader;
    final long ttlMinutes;
    final ReentrantLock refreshLock = new ReentrantLock();

    CacheEntry(String key, Function<String, Object> loader, long ttlMinutes) {
      this.key = key;
      this.loader = loader;
      this.ttlMinutes = ttlMinutes;
      // Perform initial synchronous load to ensure a value is always present.
      logger.info(String.format("Performing initial synchronous load for cache key: '%s'", key));
      this.value = this.loader.apply(key);
      updateExpiry();
      logger.info(String.format("Initial load complete for cache key: '%s'", key));
    }

    void updateExpiry() {
      this.expiryTime = Instant.now().plus(this.ttlMinutes, ChronoUnit.MINUTES);
    }
  }

  public CacheService() {}

  public Object get(final String key) {
    CacheEntry entry = caches.get(key);
    if (entry == null) {
      logger.warning(String.format("No cache entry found for key: '%s'", key));
      return null;
    }

    logger.info(
        String.format(
            "cache get(%s) expiry(%d) now(%d)",
            key, entry.expiryTime.toEpochMilli(), Instant.now().toEpochMilli()));
    // Check if the entry is stale
    if (Instant.now().isAfter(entry.expiryTime)) {
      // Entry is stale, try to acquire a lock to refresh it.
      // tryLock() is non-blocking.
      if (entry.refreshLock.tryLock()) {
        logger.info(
            String.format("Cache entry for '%s' is stale. Triggering asynchronous refresh.", key));
        // Got the lock, so this thread is responsible for triggering the refresh.
        CompletableFuture.runAsync(
            () -> {
              try {
                Object newValue = entry.loader.apply(key);
                entry.value = newValue;
                entry.updateExpiry();
                logger.info(String.format("Asynchronous refresh for '%s' complete.", key));
              } catch (Exception e) {
                logger.log(Level.SEVERE, "Error refreshing cache for key: " + key, e);
              } finally {
                // Always release the lock.
                entry.refreshLock.unlock();
              }
            },
            refreshExecutor);
      }
    }
    // Always return the current value, which might be stale.
    return entry.value;
  }

  public CacheService registerLoader(
      final String key, final Function<String, Object> loader, final long durationInMinutes) {
    if (caches.containsKey(key)) {
      logger.warning(String.format("Loader for key '%s' is already registered. Ignoring.", key));
      return this;
    }
    logger.info(
        String.format(
            "Registering cache loader for key: '%s' with TTL: %d minutes.",
            key, durationInMinutes));
    // The constructor of CacheEntry performs the initial synchronous load.
    caches.put(key, new CacheEntry(key, loader, durationInMinutes));
    return this;
  }
}
