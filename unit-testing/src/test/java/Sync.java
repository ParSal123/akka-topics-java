//package basics;

import akka.actor.testkit.typed.CapturedLogEvent;
import akka.actor.testkit.typed.Effect;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class SyncTestingSpec {
    @Test
    void testShouldSeeEffectAfterSpawningActor() {
        var testKit = BehaviorTestKit.create(SimplifiedManager.create());
        testKit.expectEffectClass(Effect.NoEffects.class);
        testKit.run(new SimplifiedManager.CreateChild("adan"));

//      This is wrong! It's only here to maintain similarity to scala code.
//      In scala, "object"s are singletons, so you can't create multiple instances of them.
//      That is why SimplifiedWorker() in the parameter list does not create a new instance.
//      In java, this would be wrong, as it creates a new instance every time.
//      As to why this succeeds, our current SimplifiedWorker is Behaviors.ignore(),
//      which returns the same instance every time under the hood. If you change it to
//      something else, the test will fail.
//      You should be using the line below for this assertion.
//      import static org.junit.jupiter.api.Assertions.assertEquals;
//      assertEquals("adan", testKit.expectEffectClass(Effect.Spawned.class).childName());
        testKit.expectEffect(new Effect.Spawned<>(SimplifiedWorker.create(), "adan", Props.empty(), null));
    }

    @Test
    void actorReceivesMessage() {
        var testKit = BehaviorTestKit.create(SimplifiedManager.create());
        var probe = TestInbox.<String>create();
        testKit.run(new SimplifiedManager.Forward("message-to-parse", probe.getRef()));
        probe.expectMessage("message-to-parse");
        assertFalse(probe.hasMessages());
    }

    @Test
    void messagesShouldBeCapturedInLogEntries() {
        var testKit = BehaviorTestKit.create(SimplifiedManager.create());
        testKit.run(SimplifiedManager.Log.getInstance());
        assertIterableEquals(testKit.getAllLogEntries(), List.of(new CapturedLogEvent(Level.INFO, "it's done")));
    }

    @Test
    void BehaviorTestKitCantDealWithScheduling() {
        var testKit = BehaviorTestKit.create(SimplifiedManager.create());
        testKit.run(new SimplifiedManager.ScheduleLog());
        testKit.expectEffect(new Effect.Scheduled<>(new FiniteDuration(1, SECONDS), testKit.getRef(),
                SimplifiedManager.Log.getInstance()));
        assertThrows(AssertionError.class, () -> assertIterableEquals(testKit.getAllLogEntries(),
                List.of(new CapturedLogEvent(Level.INFO, "it's done"))));
    }
}

class SimplifiedManager {
    public static Behavior<Command> create() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof CreateChild createChild) {
                context.spawn(SimplifiedWorker.create(), createChild.name);
                return Behaviors.same();
            } else if (message instanceof Forward forward) {
                forward.sendTo.tell(forward.message);
                return Behaviors.same();
            } else if (message instanceof ScheduleLog) {
                context.scheduleOnce(java.time.Duration.ofSeconds(1), context.getSelf(), Log.getInstance());
                return Behaviors.same();
            } else if (message instanceof Log) {
                context.getLog().info("it's done");
                return Behaviors.same();
            } else {
                // This is here to satisfy the compiler, but it should never happen.
                return Behaviors.unhandled();
            }
        });
    }

    public sealed interface Command {
    }

    public record CreateChild(String name) implements Command {
    }

    public record Forward(String message, ActorRef<String> sendTo) implements Command {
    }

    public static final class ScheduleLog implements Command {
    }

    /* Log has to be defined as a singleton, like scala.
    It's needed in the last test, where we schedule a message to be sent to the actor.
     */
    public static final class Log implements Command {
        private static final Log instance = new Log();

        private Log() {
        }

        public static Log getInstance() {
            return instance;
        }
    }
}

class SimplifiedWorker {
    public static Behavior<SimplifiedManager.Command> create() {
        return Behaviors.ignore();
    }
}