package com.datadog.appsec.powerwaf;

import datadog.trace.api.StatsDClient;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class PowerWAFStats {
  private static final String WAF_DURATION = "datadog.appsec.waf.duration";
  private static final String TAG_PREFIX_RULESET_VERSION = "ruleset_version:";
  private static final String TAG_PREFIX_WAF_VERSION = "waf_version:";
  private static final String RULES_DURATION = "datadog.appsec.event_rules.duration";

  private final StatsDClient statsDClient;
  private final String[] tagsWafDuration;
  private final ConcurrentMap<CharSequence, String> ruleIdToTagMap = new ConcurrentHashMap<>();

  public PowerWAFStats(StatsDClient statsDClient, String rulesetVersion, String wafVersion) {
    this.statsDClient = statsDClient;
    this.tagsWafDuration = new String[] {
        TAG_PREFIX_RULESET_VERSION + rulesetVersion,
        TAG_PREFIX_WAF_VERSION + wafVersion
    };
  }

  public static class RuleTagAndDuration {
    public String ruleTag; // rule_id:<rule id>
    public long durationInNanos;
  }

  public static class PWAFManagedRuleAccounting implements Iterator<RuleTagAndDuration> {
    private boolean reachedEnd = true;
    private final RuleTagAndDuration ruleTagAndDuration = new RuleTagAndDuration();

    @Override
    public boolean hasNext() {
      return false;
    }

    // the same object is returned
    @Override
    public RuleTagAndDuration next() {
      return null;
    }

    public void reset() {}
  }


  public class PerRequestAccounting {
    public final PWAFManagedRuleAccounting pwafManagedRuleAccounting = new PWAFManagedRuleAccounting();
    private final AtomicLong wafDurationAcc = new AtomicLong();

    public void recordWafDuration(long durationInNanos) {
      this.wafDurationAcc.addAndGet(durationInNanos);
    }

    public void commit() {
      long finalWafDurationInNanos = wafDurationAcc.getAndSet(0);
      statsDClient.histogram(WAF_DURATION, finalWafDurationInNanos / 1000, tagsWafDuration);

      while (pwafManagedRuleAccounting.hasNext()) {
        RuleTagAndDuration next = pwafManagedRuleAccounting.next();
        statsDClient.histogram(RULES_DURATION, next.durationInNanos / 1000, next.ruleTag);
      }

      pwafManagedRuleAccounting.reset();
    }
  }
}
