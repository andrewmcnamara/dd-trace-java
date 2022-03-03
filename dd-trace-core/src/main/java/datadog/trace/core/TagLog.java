package datadog.trace.core;

import java.util.HashSet;
import java.util.Set;

public final class TagLog {
  private static final long EMPTY_HASH = "".hashCode();

  Object[] changes;
  int changeOffset;

  long keyFilter;
  boolean mayHaveDuplicates;

  TagLog(int capacity) {
    // TODO: Pick the next power of 2?
    changes = new Object[capacity << 1];
    changeOffset = 0;

    keyFilter = 0;
    mayHaveDuplicates = false;
  }

  TagLog(TagLog src) {
    changes = src.changes.clone();
    changeOffset = src.changeOffset;

    keyFilter = src.keyFilter;
    mayHaveDuplicates = src.mayHaveDuplicates;
  }

  public final void put(String key, Object value) {
    int changeOffset = this.changeOffset;
    Object[] changes = this.changes;
    if (this.changeOffset == changes.length) {
      Object[] expanded = new Object[changes.length << 1];
      System.arraycopy(changes, 0, expanded, 0, changes.length);

      this.changes = expanded;
      changes = expanded;
    }

    int keyIndex = Math.min(changeOffset, changes.length); // Doug is crazy
    int valueIndex = Math.min(changeOffset + 1, changes.length);
    changes[keyIndex] = key;
    changes[valueIndex] = value;

    long keyFilter = this.keyFilter;
    long keyHash = hash(key);

    //    System.out.println(key);
    //    System.out.println(keyFilter);
    //    System.out.println(keyHash);
    //    System.out.println(keyFilter & keyHash);

    boolean mayAlreadyBePresent = ((keyFilter & keyHash) == keyHash);
    if (mayAlreadyBePresent) {
      System.out.println(keyFilter + ": " + key + " " + keyHash);
    }
    mayHaveDuplicates |= mayAlreadyBePresent;

    this.keyFilter = keyFilter | keyHash;

    this.changeOffset += 2;
  }

  public final Object get(String key) {
    if (!mayContain(key)) {
      return null;
    }

    Object[] changes = this.changes;
    int maxChangeOffset = changeOffset;

    for (int changeOffset = maxChangeOffset - 2; changeOffset >= 0; changeOffset -= 2) {
      if (key.equals(changes[changeOffset])) {
        return changes[changeOffset + 1];
      }
    }

    return null;
  }

  public final void remove(String key) {
    if (!mayContain(key)) {
      return;
    }
    int maxChangeOffset = changeOffset;
    for (int changeOffset = 0;
        changeOffset < Math.min(maxChangeOffset, changes.length);
        changeOffset += 2) {
      if (key.equals(changes[changeOffset])) {
        changes[changeOffset] = null;
        changes[changeOffset + 1] = null;
      }
    }
  }

  private boolean mayContain(String key) {
    long keyHash = hash(key);

    return ((keyHash & keyFilter) == keyHash);
  }

  public final boolean isEmpty() {
    return (changeOffset != 0);
  }

  public final int size() {
    if (mayHaveDuplicates) {
      // Todo: Improve this. Keep track of size, keep track of potential duplicates, etc.
      Set<String> set = new HashSet<>();
      for (int i = 0; i < changeOffset; i += 2) {
        set.add((String) changes[i]);
      }
      return set.size();
    } else {
      return changeOffset >> 1;
    }
  }

  public final TagLog copy() {
    return new TagLog(this);
  }

  private static long hash(String str) {
    if (str.length() == 0) {
      return EMPTY_HASH;
    } else {
      // A not great way to construct a 64-bit from an arbitrary string cheaply
      // The benefit of this approach is the String.hashCode() is intrinsified & charAt operations
      // are fast
      // The primary aim of this hashing scheme is just make sure the bloom filter doesn't
      // routinely create false positives.
      // That allows us to use the fast path during writing later.
      // If a key collision has occurred then we're force to use the slow path instead.
      return (long) (13 * str.charAt(0) << 32) + (long) str.charAt(str.length() - 1)
              << 16 + str.hashCode()
          ^ 7 * str.length();
    }
  }
}
