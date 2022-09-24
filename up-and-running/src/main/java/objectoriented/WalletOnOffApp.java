package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.IOException;

public class WalletOnOffApp extends AbstractBehavior<WalletOnOffApp.Command> {

    public WalletOnOffApp(ActorContext<Command> context, int credit) {
        super(context);
        this.credit=credit;
    }

    public static void main(String[] args) throws IOException {
        final ActorSystem<Command> systemGuardian = ActorSystem.create(WalletOnOffApp.create(), "walletApp");
        systemGuardian.tell(new Active());
        systemGuardian.tell(new Increase(20));
        systemGuardian.tell(new Deactivate());
        systemGuardian.tell(new Increase(10));
        systemGuardian.tell(new Active());
        systemGuardian.tell(new Increase(5));
        System.out.println("Press ENTER to terminate");
        System.in.read();
        systemGuardian.terminate();
    }
    private int credit = 0;

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Active.class, message -> {
                    getContext().getLog().debug("wallet is active");
                    return Behaviors.same();
                })
                .onMessage(Deactivate.class, message -> deactivateWallet())
                .onMessage(Increase.class, message -> {
                    credit += message.currency;
                    getContext().getLog().debug("new credit is {}",credit);
                    return Behaviors.same();
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
        return Behaviors.setup(context -> new WalletOnOffApp(context,100));
    }

    private Behavior<Command> deactivateWallet() {
        return Behaviors.receive(Command.class)
                .onMessage(Active.class, message -> createReceive())
                .onMessage(Deactivate.class, message ->{
                    getContext().getLog().debug("Wallet was deactivated ");
                    return Behaviors.same();
                })
                .onMessage(Increase.class, message -> {
                    getContext().getLog().debug("Wallet was deactivated ");
                    return Behaviors.same();
                })
                .build();
    }

}
