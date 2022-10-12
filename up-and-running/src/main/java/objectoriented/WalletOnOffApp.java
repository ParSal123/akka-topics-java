package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.io.IOException;

public class WalletOnOffApp extends AbstractBehavior<WalletOnOffApp.Command> {

  private int credit;

  public WalletOnOffApp(ActorContext<Command> context, int credit) {
    super(context);
    this.credit = credit;
  }

  public static void main(String[] args) throws IOException {
    final ActorSystem<Command> guardian =
        ActorSystem.create(WalletOnOffApp.create(), "wallet-on-off");
    guardian.tell(new Increase(1));
    guardian.tell(Deactivate.INSTANCE);
    guardian.tell(new Increase(1));
    guardian.tell(Active.INSTANCE);
    guardian.tell(new Increase(1));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(context -> new WalletOnOffApp(context, 0));
  }

  @Override
  public Receive<Command> createReceive() {
    return activated();
  }

  private Receive<Command> activated() {
    return newReceiveBuilder()
        .onMessage(
            Active.class,
            message -> {
              getContext().getLog().info("wallet is active");
              return this;
            })
        .onMessage(Deactivate.class, message -> deactivated())
        .onMessage(
            Increase.class,
            message -> {
              credit += message.currency;
              getContext().getLog().info("increasing to {}", credit);
              return this;
            })
        .build();
  }

  private Behavior<Command> deactivated() {
    return Behaviors.receive(Command.class)
        .onMessage(
            Active.class,
            message -> {
              getContext().getLog().info("activating");
              activated();
              return this;
            })
        .onMessage(Deactivate.class, message -> this)
        .onMessage(
            Increase.class,
            message -> {
              getContext().getLog().info("wallet is deactivated. Can't increase");
              return this;
            })
        .build();
  }

  public enum Active implements Command {
    INSTANCE
  }

  public enum Deactivate implements Command {
    INSTANCE
  }

  public interface Command {}

  public record Increase(int currency) implements Command {}
}
