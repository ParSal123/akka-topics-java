package fishing;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.time.Duration;

public class CounterTimer {
  public static Behavior<Command> create() {
    return resume(0);
  }

  public static Behavior<Command> resume(int count) {
    return Behaviors.receive(
        (context, message) ->
            Behaviors.withTimers(
                timer -> {
                  if (message instanceof Increase) {
                    var current = count + 1;
                    context.getLog().info("increasing to {}", current);
                    return resume(current);
                  } else if (message instanceof Pause pause) {
                    timer.startSingleTimer(new Resume(), Duration.ofSeconds(pause.seconds()));
                    return pause(count);
                  } else if (message instanceof Resume) {
                    return Behaviors.same();
                  } else {
                    // This is here to satisfy the compiler. It should never happen.
                    return Behaviors.unhandled();
                  }
                }));
  }

  public static Behavior<Command> pause(int count) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Increase) {
            context.getLog().info("counter is paused. Can't increase");
            return Behaviors.same();
          } else if (message instanceof Pause) {
            context.getLog().info("counter is paused. Can't pause again");
            return Behaviors.same();
          } else if (message instanceof Resume) {
            context.getLog().info("resuming");
            return resume(count);
          } else {
            // This is here to satisfy the compiler. It should never happen.
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public static final class Increase implements Command {}

  public record Pause(int seconds) implements Command {}

  static final class Resume implements Command {}
}
