package com.arextest.schedule.cache;

import com.arextest.common.cache.CacheProvider;
import com.arextest.common.cache.LockWrapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

/**
 * In-process {@link CacheProvider} for single-instance schedule deployments
 * without redis. Plan status keys (arex.stop.plan.* / plan_running_*), the
 * send-limiter and progress counters, the rerun caches and the locks are all
 * kept in memory.
 *
 * <p>Mirrors com.arextest.storage.cache.LocalCacheProvider; arex-common is a
 * binary dependency, so the implementation cannot be shared and each service
 * keeps its own copy.
 *
 * <p>SWITCH POINT: set arex.cache.provider=redis to go back to the shared
 * redis-backed providers (see CacheProviderConfiguration).
 *
 * <p>NOTE: with this local implementation the plan status keys are NOT shared
 * across schedule instances - PlanProduceService.stopPlan relies on the stop
 * key to reach "other instances", so deploy schedule as a single instance, or
 * switch the provider before scaling out.
 */
@Slf4j
public class LocalCacheProvider implements CacheProvider {

  private static final long NO_EXPIRE = -1L;

  private final Map<ByteArrayKey, Entry> store = new ConcurrentHashMap<>();
  private final Map<ByteArrayKey, List<byte[]>> lists = new ConcurrentHashMap<>();
  private final Map<ByteArrayKey, Long> listExpireAt = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public boolean put(byte[] key, long expiredSeconds, byte[] value) {
    store.put(new ByteArrayKey(key), new Entry(value, expireAt(expiredSeconds)));
    return true;
  }

  @Override
  public boolean put(byte[] key, byte[] value) {
    store.put(new ByteArrayKey(key), new Entry(value, NO_EXPIRE));
    return true;
  }

  @Override
  public boolean putIfAbsent(byte[] key, long expiredSeconds, byte[] value) {
    return store.putIfAbsent(new ByteArrayKey(key), new Entry(value, expireAt(expiredSeconds)))
        == null;
  }

  @Override
  public byte[] get(byte[] key) {
    ByteArrayKey wrapped = new ByteArrayKey(key);
    Entry entry = store.get(wrapped);
    if (entry == null) {
      return null;
    }
    if (entry.isExpired()) {
      store.remove(wrapped);
      return null;
    }
    return entry.value;
  }

  @Override
  public long incrValue(byte[] key) {
    return incrValueBy(key, 1L);
  }

  @Override
  public long incrValueBy(byte[] key, long value) {
    return addAndGet(key, value);
  }

  @Override
  public long decrValue(byte[] key) {
    return decrValueBy(key, 1L);
  }

  @Override
  public long decrValueBy(byte[] key, long value) {
    return addAndGet(key, -value);
  }

  private long addAndGet(byte[] key, long delta) {
    ByteArrayKey wrapped = new ByteArrayKey(key);
    Entry merged = store.merge(wrapped, new Entry(longToBytes(delta), NO_EXPIRE), (oldVal, v) -> {
      long base = oldVal.isExpired() ? 0L : bytesToLong(oldVal.value);
      return new Entry(longToBytes(base + delta), oldVal.isExpired() ? NO_EXPIRE : oldVal.expireAt);
    });
    return bytesToLong(merged.value);
  }

  @Override
  public boolean remove(byte[] key) {
    ByteArrayKey wrapped = new ByteArrayKey(key);
    boolean removed = store.remove(wrapped) != null;
    removed |= lists.remove(wrapped) != null;
    listExpireAt.remove(wrapped);
    return removed;
  }

  @Override
  public boolean expire(byte[] key, long expiredSeconds) {
    ByteArrayKey wrapped = new ByteArrayKey(key);
    boolean touched = false;
    Entry entry = store.get(wrapped);
    if (entry != null) {
      entry.expireAt = expireAt(expiredSeconds);
      touched = true;
    }
    if (lists.containsKey(wrapped)) {
      listExpireAt.put(wrapped, expireAt(expiredSeconds));
      touched = true;
    }
    return touched;
  }

  @Override
  public boolean exists(byte[] key) {
    if (liveList(new ByteArrayKey(key)) != null) {
      return true;
    }
    return get(key) != null;
  }

  @Override
  public Long rpush(byte[] key, byte[]... values) {
    ByteArrayKey wrapped = new ByteArrayKey(key);
    // a previously expired list must be dropped before appending
    liveList(wrapped);
    List<byte[]> list = lists.computeIfAbsent(wrapped, k -> new ArrayList<>());
    synchronized (list) {
      list.addAll(Arrays.asList(values));
      return (long) list.size();
    }
  }

  @Override
  public List<byte[]> lrange(byte[] key, long start, long end) {
    List<byte[]> list = liveList(new ByteArrayKey(key));
    if (list == null) {
      return Collections.emptyList();
    }
    synchronized (list) {
      int from = (int) Math.max(0, start);
      int to = end < 0 ? list.size() : (int) Math.min(list.size(), end + 1);
      if (from >= to) {
        return Collections.emptyList();
      }
      return new ArrayList<>(list.subList(from, to));
    }
  }

  /**
   * Returns the live list for the key, dropping it when its sliding expiration
   * (set by {@link #expire}) has passed.
   */
  private List<byte[]> liveList(ByteArrayKey key) {
    Long expireAt = listExpireAt.get(key);
    if (expireAt != null && expireAt < System.currentTimeMillis()) {
      lists.remove(key);
      listExpireAt.remove(key);
      return null;
    }
    return lists.get(key);
  }

  @Override
  public LockWrapper getLock(String name) {
    ReentrantLock lock = locks.computeIfAbsent(name, k -> new ReentrantLock());
    return new LockWrapper() {
      @Override
      public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit)
          throws InterruptedException {
        // leaseTime is irrelevant for an in-process lock; callers always unlock
        return lock.tryLock(waitTime, unit);
      }

      @Override
      public void lock(long leaseTime, TimeUnit unit) {
        lock.lock();
      }

      @Override
      public void unlock() {
        if (lock.isHeldByCurrentThread()) {
          lock.unlock();
        }
      }
    };
  }

  @Override
  public RedissonClient getRedissionClient() {
    // no redisson in local mode; callers must not require it (the redisson bean
    // wiring is conditional on arex.cache.provider=redis)
    return null;
  }

  /**
   * The CacheProvider expiration parameter is in SECONDS: the redis edition maps
   * it to Jedis.setex (TTL in seconds) and every caller passes a value built
   * with TimeUnit.toSeconds(...). Convert seconds -> millis here. Treating it as
   * millis made every entry expire ~1000x too soon in the storage edition, so
   * replay results vanished before the comparison ran and every case compared as
   * msgMiss / "invalid".
   */
  private static long expireAt(long expiredSeconds) {
    return expiredSeconds <= 0 ? NO_EXPIRE
        : System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiredSeconds);
  }

  private static byte[] longToBytes(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private static long bytesToLong(byte[] bytes) {
    if (bytes == null || bytes.length != Long.BYTES) {
      return 0L;
    }
    return ByteBuffer.wrap(bytes).getLong();
  }

  static String utf8(byte[] bytes) {
    return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
  }

  private static final class Entry {

    private final byte[] value;
    private volatile long expireAt;

    private Entry(byte[] value, long expireAt) {
      this.value = value;
      this.expireAt = expireAt;
    }

    private boolean isExpired() {
      return expireAt != NO_EXPIRE && System.currentTimeMillis() > expireAt;
    }
  }

  private static final class ByteArrayKey {

    private final byte[] bytes;
    private final int hash;

    private ByteArrayKey(byte[] bytes) {
      this.bytes = bytes;
      this.hash = Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ByteArrayKey && Arrays.equals(bytes, ((ByteArrayKey) o).bytes);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}
