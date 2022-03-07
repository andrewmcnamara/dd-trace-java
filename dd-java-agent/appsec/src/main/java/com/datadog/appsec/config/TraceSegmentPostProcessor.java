package com.datadog.appsec.config;

import com.datadog.appsec.report.raw.events.AppSecEvent100;
import datadog.trace.api.TraceSegment;

import java.util.Collection;

public interface TraceSegmentPostProcessor {
  void processTraceSegment(TraceSegment segment, Collection<AppSecEvent100> collectedEvents);
}
