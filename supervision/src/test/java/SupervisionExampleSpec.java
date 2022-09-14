import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.*;

import java.util.function.Supplier;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SupervisionExampleSpec {
    static final ActorTestKit testKit =
            ActorTestKit.create(ConfigFactory.parseString("akka.jvm-exit-on-fatal-error " + "= false"));


    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    @Order(1)
    void ActorExpectingASecretWillLogAndThrowExceptionAndReceivePostStopSignal() {
        var behavior = testKit.spawn(SupervisionExample.create());
        Supplier<Void> tellRecoverable = () -> {
            behavior.tell("recoverable");
            return null;
        };
        LoggingTestKit.info("recoverable").expect(testKit.system(), tellRecoverable);
    }

    @Test
    @Order(1)
    void ActorExpectingASecretWillLogAndStopAndReceivePostStopSignal() {
        var behavior = testKit.spawn(SupervisionExample.create());
        Supplier<Void> tellStop = () -> {
            behavior.tell("stop");
            return null;
        };
        LoggingTestKit.info("stopping").expect(testKit.system(), tellStop);
    }

    @Test
    @Order(1)
    void ActorExpectingASecretWillGrantAndLog() {
        var behavior = testKit.spawn(SupervisionExample.create());
        Supplier<Void> tellSecret = () -> {
            behavior.tell("secret");
            return null;
        };
        LoggingTestKit.info("granted").expect(testKit.system(), tellSecret);
    }

    /* This test is last because it will kill the Actor system
     * of the test class. */
    @Test
    @Order(2)
    void ActorExpectingASecretWillLogAndThrowExceptionAndStopTheActorSystem() {
        // Because akka.jvm-exit-on-fatal-error = false.
        // Otherwise, it will stop the jvm.
        var behavior = testKit.spawn(SupervisionExample.create());
        Supplier<Void> tellFatal = () -> {
            behavior.tell("fatal");
            return null;
        };
        LoggingTestKit.error("error").expect(testKit.system(), tellFatal);
    }
}
