import java.io.IOException;
import java.time.Duration;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

class WalletTimerApp {
    sealed interface Command {
    }

    static final class Increase implements Command {
        public final int currency;

        public Increase(int currency) {
            this.currency = currency;
        }
    }

    static final class Deactivate implements Command {
        public final int seconds;

        public Deactivate(int seconds) {
            this.seconds = seconds;
        }
    }

    private static final class Activate implements Command {
    }

    static Behavior<Command> createWallet() {
        return activated(0);
    }

    static Behavior<Command> activated(int total) {
        return Behaviors.receive((context, message) -> Behaviors.withTimers(timers -> {
            if (message instanceof Increase) {
                int current = total + ((Increase) message).currency;
                context.getLog().info("increasing to {}", current);
                return activated(current);
            } else if (message instanceof Deactivate) {
                timers.startSingleTimer(new Activate(), Duration.ofSeconds(((Deactivate) message).seconds));
                return deactivated(total);
            } else if (message instanceof Activate) {
                return Behaviors.same();
            } else {
                // This must be here to satisfy the compiler. It will never be reached.
                return Behaviors.unhandled();
            }
        }));
    }

    static Behavior<Command> deactivated(int total) {
        return Behaviors.receive((context, message) -> {
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

    public static void main(String[] args) throws IOException {
        ActorSystem<Command> guardian = ActorSystem.create(WalletTimerApp.createWallet(), "wallet-activated");
        guardian.tell(new Increase(1));
        guardian.tell(new Deactivate(3));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }
}
