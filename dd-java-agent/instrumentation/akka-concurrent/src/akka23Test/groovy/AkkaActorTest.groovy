import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.bootstrap.instrumentation.api.Tags
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.api.Checkpointer.CPU
import static datadog.trace.api.Checkpointer.END
import static datadog.trace.api.Checkpointer.SPAN
import static datadog.trace.api.Checkpointer.THREAD_MIGRATION

class AkkaActorTest extends AgentTestRunner {
  @Shared
  def akkaTester = new AkkaActors()

  def "akka actor send #name #iterations"() {
    setup:
    def barrier = akkaTester.block(name)

    when:
    (1..iterations).each {i ->
      akkaTester.send(name, "$who $i")
    }
    barrier.release()

    then:
    assertTraces(iterations) {
      (1..iterations).each {i ->
        trace(2) {
          sortSpansByStart()
          span {
            resourceName "AkkaActors.send"
            operationName "$name"
            tags {
              "$Tags.COMPONENT" "trace"
              defaultTags()
            }
          }
          span {
            resourceName "Receiver.tracedChild"
            operationName "$expectedGreeting, $who $i"
            childOf(span(0))
            tags {
              "$Tags.COMPONENT" "trace"
              defaultTags()
            }
          }
        }
      }
    }

    where:
    name      | who     | expectedGreeting | iterations
    "tell"    | "Akka"  | "Howdy"          | 1
    "ask"     | "Makka" | "Hi-diddly-ho"   | 1
    "forward" | "Pakka" | "Hello"          | 1
    "route"   | "Rakka" | "How you doin'"  | 1
    "tell"    | "Pakka" | "Howdy"          | 10
    "ask"     | "Makka" | "Hi-diddly-ho"   | 10
    "forward" | "Akka"  | "Hello"          | 10
    "route"   | "Rakka" | "How you doin'"  | 10
  }

  def "actor message handling should close leaked scopes"() {
    when:
    akkaTester.leak("Leaker", "drip")

    then:
    assertTraces(1) {
      trace(2) {
        sortSpansByStart()
        span {
          resourceName "AkkaActors.leak"
          operationName "leak all the things"
          tags {
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
        }
        span {
          resourceName("drip")
          operationName "Howdy, Leaker"
          childOf(span(0))
          tags {
            defaultTags()
          }
        }
      }
    }
  }

  def "test checkpoints emitted #name x #n"() {
    setup:
    def barrier = akkaTester.block(name)
    def threadMigrations = threadMigrationsPerWorkflow * n
    when:
    runUnderTrace("parent") {
      (1..n).each {i -> akkaTester.send(name, "who $i")}
    }
    barrier.release()
    // FIXME the expected interaction counts are stable with a pause
    //  but it should be possible to synchronise this test better
    Thread.sleep(1000)
    then:
    TEST_WRITER.waitForTraces(1)
    (2 * n + 1) * TEST_CHECKPOINTER.checkpoint(_, _, SPAN)
    threadMigrations * TEST_CHECKPOINTER.checkpoint(_, _, THREAD_MIGRATION)
    threadMigrations * TEST_CHECKPOINTER.checkpoint(_, _, THREAD_MIGRATION | END)
    // mailbox scheduling is also recorded and seems to be nondeterministic
    (threadMigrations.._) * TEST_CHECKPOINTER.checkpoint(_, _, CPU | END)
    (2 * n + 1) * TEST_CHECKPOINTER.checkpoint(_, _, SPAN | END)

    where:
    name      | n   | threadMigrationsPerWorkflow
    "ask"     | 1   | 3
    "tell"    | 1   | 3
    "route"   | 1   | 4 // 1 extra dispatch
    "forward" | 1   | 4 // 1 extra dispatch
    "ask"     | 2   | 3
    "tell"    | 2   | 3
    "route"   | 2   | 4
    "forward" | 2   | 4
    "ask"     | 10  | 3
    "tell"    | 10  | 3
    "route"   | 10  | 4
    "forward" | 10  | 4
  }
}
