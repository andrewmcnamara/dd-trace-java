package com.datadog.appsec.config;

import com.datadog.appsec.AppSecModule;
import datadog.communication.monitor.Monitoring;
import datadog.trace.api.StatsDClient;
import datadog.trace.api.interceptor.TraceInterceptor;

import java.io.Closeable;
import java.util.Optional;

public interface AppSecConfigService extends Closeable {
  void init(boolean initFleetService);

  Optional<AppSecConfig> addSubConfigListener(String key, SubconfigListener listener);

  interface SubconfigListener {
    void onNewSubconfig(AppSecConfig newConfig) throws AppSecModule.AppSecModuleActivationException;
  }

  StatsDClient getStatsDClient();

  void addTraceSegmentPostProcessor(TraceSegmentPostProcessor interceptor);

  void close();
}
