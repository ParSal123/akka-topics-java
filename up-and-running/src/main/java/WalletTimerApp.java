package state;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.io.IOException;
import java.time.Duration;

public class WalletTimerApp {

  public static void main(String[] args) throws IOException {
    ActorSystem<WalletTimer.Command> guardian =
        ActorSystem.create(WalletTimer.createWallet(), "wallet-activated");
    guardian.tell(new WalletTimer.Increase(1));
    guardian.tell(new WalletTimer.Deactivate(3));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }
}

class WalletTimer {

  public static Behavior<Command> createWallet() {
    return activated(0);
  }

  public static Behavior<Command> activated(int total) {
    return Behaviors.receive(
        (context, message) ->
            Behaviors.withTimers(
                timers -> {
                  if (message instanceof Increase increase) {
                    int current = total + increase.currency;
                    context.getLog().info("increasing to {}", current);
                    return activated(current);
                  } else if (message instanceof Deactivate deactivate) {
                    timers.startSingleTimer(new Activate(), Duration.ofSeconds(deactivate.seconds));
                    return deactivated(total);
                  } else if (message instanceof Activate) {
                    return Behaviors.same();
                  } else {
                    // This must be here to satisfy the compiler. It will never be reached.
                    return Behaviors.unhandled();
                  }
                }));
  }

  public static Behavior<Command> deactivated(int total) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Increase) {
            context.getLog().info("wallet is deactivated. Can't increase");
            return Behaviors.same();
          } else if (message instanceof Deactivate) {
            context.getLog().info("wallet is deactivated. Can't be deactivated again");
            return Behaviors.same();
          } else if (message instanceof Activate) {
            context.getLog().info("activating");
            return activated(total);
          } else {
            // This must be here to satisfy the compiler. It will never be reached.
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public record Increase(int currency) implements Command {}

  public record Deactivate(int seconds) implements Command {}

  private static final class Activate implements Command {}
}
