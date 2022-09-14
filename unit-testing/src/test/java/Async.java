package async;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.function.Function;

class AsyncTestingExampleSpec {
    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    void ActorEncoderMustReturnSameString() {
        var echo = testKit.spawn(Encoder.create(), "Akka");
        var probe = testKit.createTestProbe(String.class);
        var content = "Heellooo";
        echo.tell(new Encoder.Encode(content, probe.getRef()));
        var encoded = Base64.getEncoder().encodeToString(content.getBytes());
        probe.expectMessage(encoded);
    }

    @Test
    void SimplifiedManagerMustReceiveAMessage() {
        var manager = testKit.spawn(SimplifiedManager.create());
        var probe = testKit.createTestProbe(String.class);
        manager.tell(new SimplifiedManager.Forward("message-to-parse", probe.getRef()));
        probe.expectMessage("message-to-parse");
    }

    @Test
    void CounterMustIncreaseItsValue() {
        var counter = testKit.spawn(Counter.create(0), "counter");
        var probe = testKit.createTestProbe(Counter.State.class);
        counter.tell(new Counter.Increase());
        counter.tell(new Counter.GetState(probe.getRef()));
        probe.expectMessage(new Counter.State(1));
    }

    @Test
    void AdditionProxyMustBeAbleToDelegateIntoTheCounter() {
        var addition = testKit.spawn(AdditionProxy.create(), "addition-1");
        var probe = testKit.createTestProbe(AdditionProxy.Event.class);
        addition.tell(new AdditionProxy.Add(List.of(1, 2, 0)));
        addition.tell(new AdditionProxy.GetState(probe.getRef()));
        probe.expectMessage(new AdditionProxy.State(3));
    }

    @Test
    void ActorProxyMustRedirectMessagesToReader() {
        var proxy = testKit.spawn(Proxy.create(), "short-chain");
        var reader = testKit.createTestProbe(Reader.Text.class);
        proxy.tell(new Proxy.Send("hello", reader.getRef()));
        reader.expectMessage(new Reader.Read("hello"));
    }
}

class Encoder {
    public static Behavior<Encode> create() {
        return Behaviors.receiveMessage(sound -> {
            var encoded = Base64.getEncoder().encode(sound.content.getBytes());
            sound.sendTo.tell(new String(encoded));
            return Behaviors.same();
        });
    }

    public record Encode(String content, ActorRef<String> sendTo) {
    }

}

class Counter {

    public static Behavior<Command> create(int count) {
        return Behaviors.receiveMessage(message -> {
            if (message instanceof GetState getState) {
                getState.replyTo.tell(new State(count));
                return Behaviors.same();
            } else if (message instanceof Increase) {
                return create(count + 1);
            } else {
                // This is here to satisfy the compiler, but it should never happen.
                return Behaviors.unhandled();
            }
        });
    }

    public sealed interface Command {
    }

    public record GetState(ActorRef<State> replyTo) implements Command {
    }

    public static final class Increase implements Command {
    }

    public record State(int count) {
    }
}


class AdditionProxy {
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            var counter = context.spawnAnonymous(Counter.create(0));

            Function<ActorRef<Event>, ActorRef<Counter.State>> messageAdapter =
                    replyTo -> context.messageAdapter(Counter.State.class, state -> new AdaptState(replyTo, state));

            return Behaviors.receiveMessage(message -> {
                if (message instanceof Add add) {
                    add.numbers.forEach(number -> counter.tell(new Counter.Increase()));
                    return Behaviors.same();
                } else if (message instanceof GetState getState) {
                    counter.tell(new Counter.GetState(messageAdapter.apply(getState.replyTo)));
                    return Behaviors.same();
                } else if (message instanceof AdaptState adaptState) {
                    // adaptState.state is already a Counter.State. No need for instanceof.
                    var count = adaptState.state().count();
                    adaptState.replyTo().tell(new State(count));
                    return Behaviors.same();
                } else {
                    // This is here to satisfy the compiler, but it should never happen.
                    return Behaviors.unhandled();
                }
            });
        });
    }

    public sealed interface Command {
    }

    public sealed interface Event {
    }

    public record Add(List<Integer> numbers) implements Command {
    }

    public record GetState(ActorRef<Event> replyTo) implements Command {
    }

    public record State(int count) implements Event {
    }

    private record AdaptState(ActorRef<Event> replyTo, Counter.State state) implements Command {
    }
}

class Proxy {
    public static Behavior<Message> create() {
        return Behaviors.receiveMessage(message -> {
            if (message instanceof Send send) {
                send.sendTo.tell(new Reader.Read(send.message));
                return Behaviors.same();
            } else {
                // This is here to satisfy the compiler, but it should never happen.
                return Behaviors.unhandled();
            }
        });
    }

    public sealed interface Message {
    }

    public record Send(String message, ActorRef<Reader.Text> sendTo) implements Message {
    }
}

class Reader {

    private static final Behavior<Text> instance = Reader.create();

    public static Behavior<Text> getInstance() {
        return instance;
    }

    private static Behavior<Text> create() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Read read) {
                context.getLog().info("message: '{}', received", read.message);
                return Behaviors.same();
            } else {
                // This is here to satisfy the compiler, but it should never happen.
                return Behaviors.unhandled();
            }
        });
    }

    public sealed interface Text {

    }

    public record Read(String message) implements Text {
    }
}

class SimplifiedManager {
    public static Behavior<Command> create() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Forward forward) {
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