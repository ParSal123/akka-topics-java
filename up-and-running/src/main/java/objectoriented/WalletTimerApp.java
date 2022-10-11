package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import java.io.IOException;
import java.time.Duration;

public class WalletTimerApp extends AbstractBehavior<WalletTimerApp.Command> {
  TimerScheduler<Command> timers;
  int currency;

  public WalletTimerApp(
      ActorContext<Command> context, int currency, TimerScheduler<Command> timer) {
    super(context);
    this.currency = currency;
    this.timers = timer;
  }

  public static void main(String[] args) throws IOException {
    ActorSystem<Command> guardian = ActorSystem.create(WalletTimerApp.create(), "wallet-activated");
    guardian.tell(new Increase(1));
    guardian.tell(new Deactivate(3));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }

  public static Behavior<WalletTimerApp.Command> create() {
    return Behaviors.setup(
        context -> Behaviors.withTimers(timer -> new WalletTimerApp(context, 0, timer)));
  }

  @Override
  public Receive<Command> createReceive() {
    return activated();
  }

  private Receive<Command> activated() {
    return newReceiveBuilder()
        .onMessage(Increase.class, this::increaseWallet)
        .onMessage(Deactivate.class, this::deactivateWallet)
        .onMessage(Activate.class, unused -> this)
        .build();
  }

  private Behavior<Command> increaseWallet(Increase increase) {
    currency += increase.currency;
    getContext().getLog().info("increasing to {}", currency);
    return this;
  }

  private Behavior<Command> deactivateWallet(Deactivate deactivate) {
    getContext().getLog().info("wallet is deactivate for {} second(s)", deactivate.seconds);
    timers.startSingleTimer(Activate.INSTANCE, Duration.ofSeconds(deactivate.seconds));

    return Behaviors.receive(Command.class)
        .onMessage(
            Activate.class,
            activate -> {
              getContext().getLog().info("activating");
              return activated();
            })
        .onMessage(
            Increase.class,
            increase -> {
              getContext().getLog().info("wallet is deactivated. Can't increase");
              return this;
            })
        .onMessage(
            Deactivate.class,
            d -> {
              getContext().getLog().info("wallet is deactivated. Can't be deactivated again");
              return this;
            })
        .build();
  }

  private enum Activate implements Command {
    INSTANCE,
  }

  interface Command {}

  record Increase(int currency) implements Command {}

  record Deactivate(int seconds) implements Command {}
}
