import java.io.IOException;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

class WalletStateApp {

    public interface Command {
    }

    public static final class Increase implements Command {
        public final int currency;

        public Increase(int currency) {
            this.currency = currency;
        }
    }

    public static final class Decrease implements Command {
        public final int currency;

        public Decrease(int currency) {
            this.currency = currency;
        }
    }

    public static Behavior<Command> createWallet(int count, int max) {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Increase) {
                int current = count + ((Increase) message).currency;
                if (current <= max) {
                    context.getLog().info("increasing to {}", current);
                    return createWallet(current, max);
                } else {
                    context.getLog().info("I'm overloaded. Counting '{}' while max is '{}'. Stopping", current, max);
                    return Behaviors.same();
                }
            } else if (message instanceof Decrease) {
                int current = count - ((Decrease) message).currency;
                if (current < 0) {
                    context.getLog().info("Can't run below zero. Stopping.");
                    return Behaviors.stopped();
                } else {
                    context.getLog().info("decreasing to {}", current);
                    return createWallet(current, max);
                }
            } else {
                // This must be here to satisfy the compiler. It will never be reached.
                return Behaviors.unhandled();
            }
        });
    }

    public static void main(String[] args) throws IOException {
        ActorSystem<Command> guardian = ActorSystem.create(createWallet(0, 2), "wallet-state");
        guardian.tell(new Increase(1));
        guardian.tell(new Increase(1));
        guardian.tell(new Increase(1));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }

}
