package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.IOException;

public class WalletOnOff extends AbstractBehavior<WalletOnOff.Command> {

    public WalletOnOff(ActorContext<Command> context, int credit) {
        super(context);
        this.credit = credit;
    }

    public static void main(String[] args) throws IOException {
        final ActorSystem<Command> guardian = ActorSystem.create(WalletOnOff.create(), "wallet-on-off");
        guardian.tell(new Increase(1));
        guardian.tell(new Deactivate());
        guardian.tell(new Increase(1));
        guardian.tell(new Active());
        guardian.tell(new Increase(1));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }

    private int credit = 0;

    @Override
    public Receive<Command> createReceive() {
        return activated();
    }

    private Receive<Command> activated() {
        return newReceiveBuilder()
                .onMessage(Active.class, message -> {
                    getContext().getLog().info("wallet is active");
                    return this;
                })
                .onMessage(Deactivate.class, message -> deactivated())
                .onMessage(Increase.class, message -> {
                    credit += message.currency;
                    getContext().getLog().info("increasing to {}", credit);
                    return this;
                })
                .build();
    }

    interface Command {
    }

    static final class Active implements Command {
    }

    static final class Deactivate implements Command {
    }

    static final class Increase implements Command {
        public final int currency;

        Increase(int currency) {
            this.currency = currency;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new WalletOnOff(context, 0));
    }

    private Behavior<Command> deactivated() {
        return Behaviors.receive(Command.class)
                .onMessage(Active.class, message -> {
                    getContext().getLog().info("activating");
                    activated();
                    return this;
                })
                .onMessage(Deactivate.class, message -> this)
                .onMessage(Increase.class, message -> {
                    getContext().getLog().info("wallet is deactivated. Can't increase");
                    return this;
                })
                .build();
    }

}
