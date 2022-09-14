import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.function.Supplier;

class AsyncLogConfigSpec {
    /* TestKitJunitResource is a JUnit 4 extension for starting and stopping ActorSystem automatically
       But Junit 5 does not support @ClassRule annotation, so we can't use it!
       We need to manually start and stop the ActorSystem in for the test.
     */

    public static final ActorTestKit testKit = ActorTestKit.create(ConfigFactory.parseString("akka.es-entity" +
            ".journal-enabled = false").withFallback(ConfigFactory.load("in-memory")));

    @AfterAll
    public static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void ActorMustLogInDebugContentWhenReceivingMessage() {
        var loggerBehavior = Behaviors.<String>receive((context, message) -> {
            context.getLog().debug("message '{}', received", message);
            return Behaviors.same();
        });
        var loggerActor = testKit.spawn(loggerBehavior);
        var message = "hi";
        Supplier<Void> tellHiToLoggerActor = () -> {
            loggerActor.tell(message);
            return null;
        };
        LoggingTestKit.debug(String.format("message '%s', received", message)).expect(testKit.system(),
                tellHiToLoggerActor);
    }

    @Test
    void ActorMustLiftOnePropertyFromConf() {
        var inMemory = testKit.system().settings().config();
        var journalEnabled = inMemory.getString("akka.es-entity.journal-enabled");
        var readJournal = inMemory.getString("akka.es-entity.read-journal");

        var loggerBehavior = Behaviors.<String>receive((context, message) -> {
            context.getLog().info("{} {}", journalEnabled, readJournal);
            return Behaviors.same();
        });

        var loggerActor = testKit.spawn(loggerBehavior);
        var message = "anyMessage";

        Supplier<Void> tellMessageToActor = () -> {
            loggerActor.tell(message);
            return null;
        };

        LoggingTestKit.info("false inmem-read-journal").expect(testKit.system(), tellMessageToActor);
    }

    @Test
    void ActorMustLogMessagesToDeadLetters() {
        var behavior = Behaviors.<String>stopped();
        var carl = testKit.spawn(behavior, "carl");

        Supplier<Void> tellHelloToCarl = () -> {
            carl.tell("Hello");
            carl.tell("Hello");
            return null;
        };

        LoggingTestKit.empty().withLogLevel(Level.INFO).withMessageRegex(".*Message.*to.*carl.*was not delivered.*2" +
                ".*dead letters encountered").expect(testKit.system(), tellHelloToCarl);
    }
}

