package com.arextest.schedule.dao;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One-to-one matcher between stored rows and incoming rows of the same
 * business key, pairing duplicates of a key in row order so a rewrite reuses
 * the stored row id instead of generating a new one.
 */
public final class RowKeyMatcher {

  private RowKeyMatcher() {
  }

  /**
   * @param existing stored rows, in a stable order
   * @param keyOf    business key of a row
   * @return function returning the next unconsumed stored row with the same
   *         key as the given incoming row, or null when none is left
   */
  public static <E> Function<E, E> matchInOrder(List<E> existing, Function<E, String> keyOf) {
    Map<String, Deque<E>> byKey = new HashMap<>();
    for (E row : existing) {
      byKey.computeIfAbsent(keyOf.apply(row), k -> new ArrayDeque<>()).add(row);
    }
    return incoming -> byKey.getOrDefault(keyOf.apply(incoming), new ArrayDeque<>()).poll();
  }
}
