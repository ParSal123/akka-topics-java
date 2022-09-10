import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.io.IOException;

public class WalletOnOffApp {

    public static Behavior<Command> createWallet() {
        return activated(0);
    }

    public static Behavior<Command> activated(int count) {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Increase increase) {
                int current = count + increase.currency;
                context.getLog().info("increasing to {}", current);
                return activated(current);
            } else if (message instanceof Deactivate) {
                return deactivated(count);
            } else if (message instanceof Activate) {
                return Behaviors.same();
            } else {
                // This must be here to satisfy the compiler. It will never be reached.
                return Behaviors.unhandled();
            }
        });
    }

    public static Behavior<Command> deactivated(int count) {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Increase) {
                context.getLog().info("wallet is deactivated. Can't increase");
                return Behaviors.same();
            } else if (message instanceof Deactivate) {
                return Behaviors.same();
            } else if (message instanceof Activate) {
                context.getLog().info("activating");
                return activated(count);
            } else {
                // This must be here to satisfy the compiler. It will never be reached.
                return Behaviors.unhandled();
            }
        });
    }

    public static void main(String[] args) throws IOException {
        ActorSystem<Command> guardian = ActorSystem.create(createWallet(), "wallet-on-off");
        guardian.tell(new Increase(1));
        guardian.tell(new Deactivate());
        guardian.tell(new Increase(1));
        guardian.tell(new Activate());
        guardian.tell(new Increase(1));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }

    public sealed interface Command {
    }

    public record Increase(int currency) implements Command {
    }

    public static final class Deactivate implements Command {
    }

    public static final class Activate implements Command {
    }
}