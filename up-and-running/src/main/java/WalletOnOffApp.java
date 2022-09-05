import java.io.IOException;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

class WalletOnOffApp {

    sealed interface Command {
    }

    static final class Increase implements Command {
        public final int currency;

        public Increase(int currency) {
            this.currency = currency;
        }
    }

    static final class Deactivate implements Command {
    }

    static final class Activate implements Command {
    }

    static Behavior<Command> createWallet() {
        return activated(0);
    }

    static Behavior<Command> activated(int count) {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Increase) {
                int current = count + ((Increase) message).currency;
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

    static Behavior<Command> deactivated(int count) {
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
}