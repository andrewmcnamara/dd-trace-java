package datadog.trace.agent.tooling.bytebuddy.matcher;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Additional global matchers that are used to reduce number of classes we try to apply expensive
 * matchers to.
 *
 * <p>This is separated from {@link GlobalIgnoresMatcher} to allow for better testing. The idea is
 * that we should be able to remove this matcher from the agent and all tests should still pass.
 * Moreover, no classes matched by this matcher should be modified during test run.
 */
public class AdditionalLibraryIgnoresMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.ForNonNullValues<T> {

  public static <T extends TypeDescription> Junction<T> additionalLibraryIgnoresMatcher() {
    return new AdditionalLibraryIgnoresMatcher<>();
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  protected boolean doMatch(final T target) {
    final String name = target.getActualName();

    if (name.startsWith("com.beust.jcommander.")
        || name.startsWith("com.fasterxml.classmate.")
        || name.startsWith("com.github.mustachejava.")
        || name.startsWith("com.jayway.jsonpath.")
        || name.startsWith("com.lightbend.lagom.")
        || name.startsWith("javax.el.")
        || name.startsWith("net.sf.cglib.")
        || name.startsWith("org.apache.lucene.")
        || name.startsWith("org.apache.tartarus.")
        || name.startsWith("org.json.simple.")
        || name.startsWith("org.yaml.snakeyaml.")) {
      return true;
    }

    if (name.startsWith("org.springframework.")) {
      if ((name.startsWith("org.springframework.aop.")
              && !name.equals("org.springframework.aop.interceptor.AsyncExecutionInterceptor"))
          || name.startsWith("org.springframework.cache.")
          || name.startsWith("org.springframework.dao.")
          || name.startsWith("org.springframework.ejb.")
          || name.startsWith("org.springframework.expression.")
          || name.startsWith("org.springframework.format.")
          || name.startsWith("org.springframework.jca.")
          || name.startsWith("org.springframework.jdbc.")
          || name.startsWith("org.springframework.jmx.")
          || name.startsWith("org.springframework.jndi.")
          || name.startsWith("org.springframework.lang.")
          || name.startsWith("org.springframework.messaging.")
          || name.startsWith("org.springframework.objenesis.")
          || name.startsWith("org.springframework.orm.")
          || name.startsWith("org.springframework.remoting.")
          || name.startsWith("org.springframework.scripting.")
          || name.startsWith("org.springframework.stereotype.")
          || name.startsWith("org.springframework.transaction.")
          || name.startsWith("org.springframework.ui.")
          || name.startsWith("org.springframework.validation.")) {
        return true;
      }

      if (name.startsWith("org.springframework.data.")) {
        if (name.equals("org.springframework.data.repository.core.support.RepositoryFactorySupport")
            || name.startsWith(
                "org.springframework.data.convert.ClassGeneratingEntityInstantiator$")
            || name.equals(
                "org.springframework.data.jpa.repository.config.InspectionClassLoader")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.amqp.")) {
        return false;
      }

      if (name.startsWith("org.springframework.beans.")) {
        if (name.equals("org.springframework.beans.factory.support.DisposableBeanAdapter")
            || name.startsWith(
                "org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader$")
            || name.equals("org.springframework.beans.factory.support.AbstractBeanFactory")
            || name.equals(
                "org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory")
            || name.equals(
                "org.springframework.beans.factory.support.DefaultListableBeanFactory")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.boot.")) {
        // More runnables to deal with
        if (name.startsWith("org.springframework.boot.autoconfigure.BackgroundPreinitializer$")
            || name.startsWith("org.springframework.boot.autoconfigure.condition.OnClassCondition$")
            || name.startsWith("org.springframework.boot.web.embedded.netty.NettyWebServer$")
            || name.startsWith("org.springframework.boot.web.embedded.tomcat.TomcatWebServer$1")
            || name.startsWith(
                "org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer$")
            || name.equals(
                "org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedWebappClassLoader")
            || name.equals(
                "org.springframework.boot.web.embedded.tomcat.TomcatEmbeddedWebappClassLoader")
            || name.equals(
                "org.springframework.boot.context.embedded.EmbeddedWebApplicationContext")
            || name.equals(
                "org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext")
            || name.equals(
                "org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext")
            || name.equals(
                "org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.cglib.")) {
        // LoadingCache.createEntry constructs FutureTasks which it executes synchronously,
        // which leads to pointless context propagation and checkpoint emission, so we need
        // to instrument this class to disable async propagation to make the tests happy
        return !name.startsWith("org.springframework.cglib.core.internal.LoadingCache");
      }

      if (name.startsWith("org.springframework.context.")) {
        // More runnables to deal with
        if (name.startsWith("org.springframework.context.support.AbstractApplicationContext$")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.core.")) {
        if (name.startsWith("org.springframework.core.task.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.instrument.")) {
        return true;
      }

      if (name.startsWith("org.springframework.http.")) {
        // There are some Mono implementation that get instrumented
        if (name.startsWith("org.springframework.http.server.reactive.")) {
          return false;
        }
        if (name.endsWith("HttpMessageConverter")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.jms.")) {
        if (name.startsWith("org.springframework.jms.listener.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.util.")) {
        if (name.startsWith("org.springframework.util.concurrent.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.web.")) {
        if (name.startsWith("org.springframework.web.servlet.")
            || name.startsWith("org.springframework.web.reactive.")
            || name.startsWith("org.springframework.web.context.request.async.")
            || name.equals(
                "org.springframework.web.context.support.AbstractRefreshableWebApplicationContext")
            || name.equals("org.springframework.web.context.support.GenericWebApplicationContext")
            || name.equals("org.springframework.web.context.support.XmlWebApplicationContext")) {
          return false;
        }
        return true;
      }

      return false;
    }

    // xml-apis, xerces, xalan
    if (name.startsWith("javax.xml.")
        || name.startsWith("org.apache.bcel.")
        || name.startsWith("org.apache.html.")
        || name.startsWith("org.apache.regexp.")
        || name.startsWith("org.apache.wml.")
        || name.startsWith("org.apache.xalan.")
        || name.startsWith("org.apache.xerces.")
        || name.startsWith("org.apache.xml.")
        || name.startsWith("org.apache.xpath.")
        || name.startsWith("org.xml.")) {
      return true;
    }

    if (name.startsWith("ch.qos.logback.")) {
      // We instrument this Runnable
      if (name.equals("ch.qos.logback.core.AsyncAppenderBase$Worker")) {
        return false;
      }

      if (name.startsWith("ch.qos.logback.classic.spi.LoggingEvent")) {
        return false;
      }

      if (name.equals("ch.qos.logback.classic.Logger")) {
        return false;
      }

      return true;
    }

    if (name.startsWith("org.apache.log4j.")) {
      return !name.equals("org.apache.log4j.MDC")
          && !name.equals("org.apache.log4j.spi.LoggingEvent")
          && !name.equals("org.apache.log4j.Category");
    }

    if (name.startsWith("com.codahale.metrics.")) {
      // We instrument servlets
      if (name.startsWith("com.codahale.metrics.servlets.")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.couchbase.client.deps.")) {
      // Couchbase library includes some packaged dependencies, unfortunately some of them are
      // instrumented by java-concurrent instrumentation
      if (name.startsWith("com.couchbase.client.deps.io.netty.")
          || name.startsWith("com.couchbase.client.deps.org.LatencyUtils.")
          || name.startsWith("com.couchbase.client.deps.com.lmax.disruptor.")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.google.cloud.")
        || name.startsWith("com.google.instrumentation.")
        || name.startsWith("com.google.j2objc.")
        || name.startsWith("com.google.gson.")
        || name.startsWith("com.google.logging.")
        || name.startsWith("com.google.longrunning.")
        || name.startsWith("com.google.protobuf.")
        || name.startsWith("com.google.rpc.")
        || name.startsWith("com.google.thirdparty.")
        || name.startsWith("com.google.type.")) {
      return true;
    }
    if (name.startsWith("com.google.common.")) {
      if (name.startsWith("com.google.common.util.concurrent.")
          || name.equals("com.google.common.base.internal.Finalizer")) {
        return false;
      }
      return true;
    }
    if (name.startsWith("com.google.inject.")) {
      // We instrument Runnable there
      if (name.startsWith("com.google.inject.internal.AbstractBindingProcessor$")
          || name.startsWith("com.google.inject.internal.BytecodeGen$")
          || name.startsWith("com.google.inject.internal.cglib.core.internal.$LoadingCache$")) {
        return false;
      }
      return true;
    }
    if (name.startsWith("com.google.api.")) {
      if (name.startsWith("com.google.api.client.http.HttpRequest")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("org.h2.")) {
      if (name.equals("org.h2.Driver")
          || name.startsWith("org.h2.jdbc.")
          || name.startsWith("org.h2.jdbcx.")
          // Some runnables that get instrumented
          || name.equals("org.h2.util.Task")
          || name.equals("org.h2.util.MathUtils$1")
          || name.equals("org.h2.store.FileLock")
          || name.equals("org.h2.engine.DatabaseCloser")
          || name.equals("org.h2.engine.OnExitDatabaseCloser")
          || name.equals("org.h2.tools.Server")
          || name.equals("org.h2.store.WriterThread")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.carrotsearch.hppc.")) {
      if (name.startsWith("com.carrotsearch.hppc.HashOrderMixing$")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.fasterxml.jackson.")) {
      if (name.equals("com.fasterxml.jackson.module.afterburner.util.MyClassLoader")) {
        return false;
      }
      return true;
    }

    // kotlin, note we do not ignore kotlinx because we instrument coroutines code
    if (name.startsWith("kotlin.")) {
      return true;
    }

    if (name.startsWith("scala.collection.")) {
      // saves ~0.5s skipping instrumentation of almost ~470 classes
      return true;
    }

    if (name.startsWith("akka.")) {
      if (name.startsWith("akka.http.")) {
        // saves ~0.1s skipping ~233 classes
        if (name.startsWith("akka.http.scaladsl.")) {
          if (name.equals("akka.http.scaladsl.HttpExt")
              || name.equals("akka.http.scaladsl.Http2Ext")) {
            return false;
          }
          return true;
        }
        // saves ~0.1s skipping ~272 classes
        if (name.startsWith("akka.http.impl.")) {
          if (name.equals("akka.http.impl.engine.client.PoolMasterActor")
              || name.equals("akka.http.impl.engine.http2.Http2Ext")
              || name.startsWith(
                  "akka.http.impl.engine.server.HttpServerBluePrint$TimeoutAccessImpl$")
              || name.startsWith("akka.http.impl.engine.client.pool.NewHostConnectionPool$")
              || name.startsWith("akka.http.impl.util.StreamUtils$")) {
            return false;
          }
          return true;
        }
      }

      // saves ~0.1s skipping ~320 classes
      if (name.startsWith("akka.actor.")) {
        if (name.startsWith("akka.actor.LightArrayRevolverScheduler$")
            || name.startsWith("akka.actor.Scheduler$")
            || name.startsWith("akka.actor.ActorSystemImpl$")
            || name.startsWith("akka.actor.CoordinatedShutdown$")
            || name.startsWith("akka.actor.ActorSystem$")
            || name.equals("akka.actor.ActorCell")) {
          return false;
        }
        return true;
      }

      // saves ~0.1s skipping ~407 classes
      if (name.startsWith("akka.stream.")) {
        if (name.startsWith("akka.stream.impl.fusing.ActorGraphInterpreter$")
            || name.equals("akka.stream.impl.FanOut$SubstreamSubscription")
            || name.equals("akka.stream.impl.FanIn$SubInput")
            || name.startsWith("akka.stream.stage.TimerGraphStageLogic$")
            || name.startsWith("akka.stream.stage.GraphStageLogic$")) {
          return false;
        }
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return "additionalLibraryIgnoresMatcher()";
  }

  @Override
  public boolean equals(final Object other) {
    if (!super.equals(other)) {
      return false;
    } else if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else {
      return getClass() == other.getClass();
    }
  }

  @Override
  public int hashCode() {
    return 17;
  }
}
