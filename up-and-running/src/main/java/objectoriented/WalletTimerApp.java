package objectoriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.io.IOException;
import java.time.Duration;

public class WalletTimerApp extends AbstractBehavior<WalletTimerApp.Command> {
    public static void main(String[] args) throws IOException, InterruptedException {
        ActorSystem<Command> guardian = ActorSystem.create(WalletTimerApp.create(100), "wallet-activated");
        guardian.tell(new Increase(1));
        guardian.tell(new Deactivate(3));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }
    TimerScheduler<Command> timers;
    int currency = 0;

    public WalletTimerApp(ActorContext<Command> context, int currency, TimerScheduler<Command> timer) {
        super(context);
        this.currency = currency;
        this.timers = timer;
    }

    interface Command {
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


    public static Behavior<WalletTimerApp.Command> create(int currency) {
        return Behaviors.setup(context -> Behaviors.withTimers(timer -> new WalletTimerApp(context, currency, timer)));
    }

    @Override
    public Receive<Command> createReceive() {
        return activate();
    }

    private Receive<Command> activate() {
        return newReceiveBuilder()
                .onMessage(Increase.class, this::increaseWallet)
                .onMessage(Deactivate.class, this::deactivateWallet)
                .build();
    }

    private Behavior<Command> increaseWallet(Increase increase) {
        currency += increase.currency;
        getContext().getLog().info("increasing to {}", currency);
        return this;
    }

    private Behavior<Command> deactivateWallet(Deactivate deactivate) {
        getContext().getLog().info("wallet is deactivate for {} second(s)", deactivate.seconds);
        timers.startSingleTimer(new Activate(), Duration.ofSeconds(deactivate.seconds));

        return Behaviors.receive(Command.class).onMessage(Activate.class, activate -> {
                    getContext().getLog().info("activating");
                    return activate();
                })
                .onMessage(Increase.class, increase -> {
                    getContext().getLog().info("wallet is deactivated. Can't increase");
                    return this;
                }).onMessage(Deactivate.class, d -> {
                    getContext().getLog().info("wallet is deactivated. Can't be deactivated again");
                    return this;
                })
                .build();
    }

}
