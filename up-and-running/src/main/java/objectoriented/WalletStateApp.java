package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.io.IOException;

public class WalletStateApp extends AbstractBehavior<WalletStateApp.Command> {
  int currency;
  int max;

  private WalletStateApp(ActorContext<Command> context, int currency, int max) {
    super(context);
    this.currency = currency;
    this.max = max;
  }

  public static Behavior<Command> create(int currency, int max) {
    return Behaviors.setup(context -> new WalletStateApp(context, currency, max));
  }

  public static void main(String[] args) throws IOException {
    ActorSystem<Command> guardian = ActorSystem.create(WalletStateApp.create(0, 2), "wallet-state");
    guardian.tell(new Increase(1));
    guardian.tell(new Increase(1));
    guardian.tell(new Increase(1));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Increase.class, this::increaseWallet)
        .onMessage(Decrease.class, this::decreaseWallet)
        .build();
  }

  private Behavior<Command> increaseWallet(Increase increase) {
    var current = currency + increase.currency;
    if (current <= max) {
      getContext().getLog().info("increasing to {}", current);
      currency += increase.currency;
      return this;
    }
    getContext()
        .getLog()
        .info("I'm overloaded. Counting '{}' while max is '{}'. Stopping", current, max);
    return this;
  }

  private Behavior<Command> decreaseWallet(Decrease decrease) {
    var current = currency - decrease.currency;
    if (current < 0) {
      getContext().getLog().info("Can't run below zero. Stopping.");
      return Behaviors.stopped();
    }
    currency -= decrease.currency;
    getContext().getLog().info("decreasing to {}", currency);
    return this;
  }

  public sealed interface Command {}

  public record Increase(int currency) implements Command {}

  public record Decrease(int currency) implements Command {}
}
