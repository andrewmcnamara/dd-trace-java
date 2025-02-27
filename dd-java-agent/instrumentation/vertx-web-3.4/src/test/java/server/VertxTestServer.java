package server;

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.FORWARDED;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_ENCODED_BOTH;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_ENCODED_QUERY;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS;
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.UNKNOWN;
import static datadog.trace.agent.test.utils.TraceUtils.runnableUnderTraceAsync;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import datadog.trace.agent.test.base.HttpServerTest;
import datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class VertxTestServer extends AbstractVerticle {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

  @Override
  public void start(final Future<Void> startFuture) {
    final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = Router.router(vertx);

    customizeBeforeRoutes(router);

    router
        .route(SUCCESS.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    SUCCESS,
                    () ->
                        ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody())));
    router
        .route(FORWARDED.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    FORWARDED,
                    () ->
                        ctx.response()
                            .setStatusCode(FORWARDED.getStatus())
                            .end(ctx.request().getHeader("x-forwarded-for"))));
    router
        .route(QUERY_ENCODED_BOTH.getRawPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    QUERY_ENCODED_BOTH,
                    () ->
                        ctx.response()
                            .setStatusCode(QUERY_ENCODED_BOTH.getStatus())
                            .end(QUERY_ENCODED_BOTH.bodyForQuery(ctx.request().query()))));
    router
        .route(QUERY_ENCODED_QUERY.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    QUERY_ENCODED_QUERY,
                    () ->
                        ctx.response()
                            .setStatusCode(QUERY_ENCODED_QUERY.getStatus())
                            .end(QUERY_ENCODED_QUERY.bodyForQuery(ctx.request().query()))));
    router
        .route(QUERY_PARAM.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    QUERY_PARAM,
                    () ->
                        ctx.response()
                            .setStatusCode(QUERY_PARAM.getStatus())
                            .end(ctx.request().query())));
    router
        .route("/path/:id/param")
        .handler(
            ctx ->
                controller(
                    ctx,
                    PATH_PARAM,
                    () ->
                        ctx.response()
                            .setStatusCode(PATH_PARAM.getStatus())
                            .end(ctx.request().getParam("id"))));
    router
        .route(REDIRECT.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    REDIRECT,
                    () ->
                        ctx.response()
                            .setStatusCode(REDIRECT.getStatus())
                            .putHeader("location", REDIRECT.getBody())
                            .end()));
    router
        .route(ERROR.getPath())
        .handler(
            ctx ->
                controller(
                    ctx,
                    ERROR,
                    () -> ctx.response().setStatusCode(ERROR.getStatus()).end(ERROR.getBody())));
    router
        .route(EXCEPTION.getPath())
        .handler(ctx -> controller(ctx, EXCEPTION, VertxTestServer::exception));

    router = customizeAfterRoutes(router);

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port, event -> startFuture.complete());
  }

  protected void customizeBeforeRoutes(Router router) {}

  protected Router customizeAfterRoutes(final Router router) {
    return router;
  }

  private static void exception() {
    throw new RuntimeException(EXCEPTION.getBody());
  }

  private static void controller(
      RoutingContext ctx, final ServerEndpoint endpoint, final Runnable runnable) {
    assert activeSpan() != null : "Controller should have a parent span.";
    assert activeScope().isAsyncPropagating() : "Scope should be propagating async.";
    ctx.response()
        .putHeader(
            HttpServerTest.getIG_RESPONSE_HEADER(), HttpServerTest.getIG_RESPONSE_HEADER_VALUE());
    if (endpoint == NOT_FOUND || endpoint == UNKNOWN) {
      runnable.run();
      return;
    }
    runnableUnderTraceAsync("controller", runnable);
  }
}
