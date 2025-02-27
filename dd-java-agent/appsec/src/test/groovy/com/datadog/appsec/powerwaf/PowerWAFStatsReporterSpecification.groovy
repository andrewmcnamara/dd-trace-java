package com.datadog.appsec.powerwaf

import com.datadog.appsec.gateway.AppSecRequestContext
import datadog.trace.api.TraceSegment
import datadog.trace.test.util.DDSpecification
import io.sqreen.powerwaf.PowerwafMetrics

class PowerWAFStatsReporterSpecification extends DDSpecification {
  PowerWAFStatsReporter reporter = new PowerWAFStatsReporter()
  AppSecRequestContext ctx = Mock()

  void 'reporter reports timings'() {
    setup:
    PowerwafMetrics metrics = new PowerwafMetrics()
    metrics.totalRunTimeNs = 2_000
    metrics.totalDdwafRunTimeNs = 1_000
    TraceSegment segment = Mock()

    when:
    reporter.processTraceSegment(segment, ctx, [])

    then:
    1 * ctx.getWafMetrics() >> metrics
    1 * segment.setTagTop('_dd.appsec.waf.duration', 1)
    1 * segment.setTagTop('_dd.appsec.waf.duration_ext', 2)
  }

  void 'reports nothing if metrics are null'() {
    setup:
    TraceSegment segment = Mock()

    when:
    reporter.processTraceSegment(segment, ctx, [])

    then:
    1 * ctx.getWafMetrics() >> null
    0 * segment._(*_)
  }
}
