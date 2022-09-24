import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class AsyncLogSpec {

  static ActorTestKit testKit = ActorTestKit.create();

  @AfterAll
  static void teardown() {
    testKit.shutdownTestKit();
  }

  @Test
  void SimplifiedManagerMustBeAbleToLogItsDone() {
    var manager = testKit.spawn(SimplifiedManager.create(), "manager");
    Supplier<Void> tellLogToManager =
        () -> {
          manager.tell(new SimplifiedManager.Log());
          return null;
        };
    LoggingTestKit.info("it's done").expect(testKit.system(), tellLogToManager);
  }
}

class Proxy {
  public static Behavior<Message> create() {
    return Behaviors.receiveMessage(
        message -> {
          Send send = (Send) message;
          send.sendTo.tell(send.message);
          return Behaviors.same();
        });
  }

  public sealed interface Message {}

  public record Send(String message, ActorRef<String> sendTo) implements Message {}
}

class Listener {
  public static Behavior<String> create() {
    return Behaviors.receive(
        (context, message) -> {
          context.getLog().info("message '{}', received", message);
          return Behaviors.same();
        });
  }
}

class SimplifiedManager {
  public static Behavior<Command> create() {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Log) {
            context.getLog().info("it's done");
            return Behaviors.same();
          } else {
            // This is here to satisfy the compiler, but it should never happen.
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public static final class Log implements Command {}
}
