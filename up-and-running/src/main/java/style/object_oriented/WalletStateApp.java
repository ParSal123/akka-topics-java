package style.object_oriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.IOException;

public class WalletStateApp extends AbstractBehavior<WalletStateApp.Command> {
    int currency = 0;
    int max = 0;

    interface Command {
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

    public static Behavior<Command> create( int currency, int max) {
        return Behaviors.setup(context -> new WalletStateApp(context, currency,max));
    }

    public WalletStateApp(ActorContext<Command> context, int currency, int max) {
        super(context);
        this.currency = currency;
        this.max = max;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Increase.class, this::increaseWallet)
                .onMessage(Decrease.class, this::decreaseWallet)
                .build();
    }

    private Behavior<Command> increaseWallet(Increase increase) {
        currency += increase.currency;
        int current = currency + increase.currency;
        if (current <= max) {
            getContext().getLog().info("increasing to {}", current);
            return this;
        }
        getContext().getLog().info("I'm overloaded. Counting '{}' while max is '{}'. Stopping", current, max);
        return Behaviors.same();
    }

    private Behavior<Command> decreaseWallet(Decrease decrease) {

        currency = currency - decrease.currency;
        if (currency < 0) {
            getContext().getLog().info("Can't run below zero. Stopping.");
            return Behaviors.stopped();
        } else {
            getContext().getLog().info("decreasing to {}", currency);
            return this;
        }
    }

    public static void main(String[] args) throws IOException {
        ActorSystem<Command> guardian = ActorSystem.create(WalletStateApp.create(0, 2), "wallet-state");
        guardian.tell(new Increase(1));
        guardian.tell(new Decrease(1));
        guardian.tell(new Decrease(1));
        guardian.tell(new Increase(1));
        guardian.tell(new Increase(1));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }
}
