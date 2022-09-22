import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.io.IOException;

public class WalletStateApp {

  public static void main(String[] args) throws IOException {
    ActorSystem<WalletState.Command> guardian =
        ActorSystem.create(WalletState.create(0, 2), "wallet-state");
    guardian.tell(new WalletState.Increase(1));
    guardian.tell(new WalletState.Increase(1));
    guardian.tell(new WalletState.Increase(1));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }
}

class WalletState {

  public static Behavior<Command> create(int count, int max) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Increase increase) {
            int current = count + increase.currency;
            if (current <= max) {
              context.getLog().info("increasing to {}", current);
              return create(current, max);
            } else {
              context
                  .getLog()
                  .info("I'm overloaded. Counting '{}' while max is '{}'. Stopping", current, max);
              return Behaviors.same();
            }
          } else if (message instanceof Decrease decrease) {
            int current = count - decrease.currency;
            if (current < 0) {
              context.getLog().info("Can't run below zero. Stopping.");
              return Behaviors.stopped();
            } else {
              context.getLog().info("decreasing to {}", current);
              return create(current, max);
            }
          } else {
            // This must be here to satisfy the compiler. It will never be reached.
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public record Increase(int currency) implements Command {}

  public record Decrease(int currency) implements Command {}
}
