package datadog.trace.instrumentation.tomcat55;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.api.gateway.Events.EVENTS;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.muzzle.IReferenceMatcher;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher;
import datadog.trace.api.function.BiFunction;
import datadog.trace.api.gateway.CallbackProvider;
import datadog.trace.api.gateway.Flow;
import datadog.trace.api.gateway.RequestContext;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import java.util.Hashtable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.tomcat.util.http.Parameters;

@AutoService(Instrumenter.class)
public class ParsedBodyParametersInstrumentation extends Instrumenter.AppSec
    implements Instrumenter.ForSingleType {

  public ParsedBodyParametersInstrumentation() {
    super("tomcat");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // class that's not present in tomcat 5.0.x, which we don't support in the tomcat-5.5 instr
    return hasClassesNamed("org.apache.tomcat.util.buf.StringCache");
  }

  @Override
  public String instrumentedType() {
    return "org.apache.tomcat.util.http.Parameters";
  }

  // paramHashStringArray was only final for a few days. it doesn't seem to have made into a release
  private static final ReferenceMatcher PARAM_HASH_STRING_ARRAY_REFERENCE_MATCHER =
      new ReferenceMatcher(
          new Reference.Builder("org.apache.tomcat.util.http.Parameters")
              .withField(
                  new String[0],
                  Reference.EXPECTS_NON_FINAL,
                  "paramHashStringArray",
                  "Ljava/util/Hashtable;")
              .build());

  private IReferenceMatcher postProcessReferenceMatcher(final ReferenceMatcher origMatcher) {
    return new IReferenceMatcher.ConjunctionReferenceMatcher(
        origMatcher, PARAM_HASH_STRING_ARRAY_REFERENCE_MATCHER);
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        // also matches the variant taking an extra encoding parameter
        named("processParameters")
            .and(takesArgument(0, byte[].class))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class)),
        getClass().getName() + "$ProcessParametersAdvice");

    transformation.applyAdvice(
        named("handleQueryParameters").and(takesArguments(0)),
        getClass().getName() + "$HandleQueryParametersAdvice");
  }

  // skip advice in processParameters if we're inside handleQueryParameters()
  public static class HandleQueryParametersAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    static int before() {
      return CallDepthThreadLocalMap.incrementCallDepth(Parameters.class);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    static void after(@Advice.Enter final int depth) {
      if (depth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(Parameters.class);
    }
  }

  @SuppressWarnings("Duplicates")
  public static class ProcessParametersAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    static int before(
        @Advice.FieldValue(value = "paramHashStringArray", readOnly = false)
            Hashtable<String, String[]> paramValuesField,
        @Advice.Local("origParamHashStringArray") Hashtable<String, String[]> origParamValues) {
      int depth = CallDepthThreadLocalMap.incrementCallDepth(Parameters.class);
      if (depth == 0 && !paramValuesField.isEmpty()) {
        origParamValues = paramValuesField;
        paramValuesField = new Hashtable<>();
      }
      return depth;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    static void after(
        @Advice.Local("origParamHashStringArray") Hashtable<String, String[]> origParamValues,
        @Advice.FieldValue(value = "paramHashStringArray", readOnly = false)
            Hashtable<String, String[]> paramValuesField,
        @Advice.Enter final int depth) {
      if (depth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(Parameters.class);

      try {
        if (paramValuesField.isEmpty()) {
          return;
        }

        AgentSpan agentSpan = activeSpan();
        if (agentSpan == null) {
          return;
        }

        CallbackProvider cbp = AgentTracer.get().instrumentationGateway();
        BiFunction<RequestContext<Object>, Object, Flow<Void>> callback =
            cbp.getCallback(EVENTS.requestBodyProcessed());
        RequestContext<Object> requestContext = agentSpan.getRequestContext();
        if (requestContext == null || callback == null) {
          return;
        }
        callback.apply(requestContext, paramValuesField);
      } finally {
        if (origParamValues != null) {
          origParamValues.putAll(paramValuesField);
          paramValuesField = origParamValues;
        }
      }
    }
  }
}
