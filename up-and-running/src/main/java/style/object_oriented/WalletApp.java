package style.object_oriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.IOException;

public class WalletApp extends AbstractBehavior<Integer> {
    int credit = 0;

    public static void main(String[] args) throws IOException {
        final ActorSystem<Integer> systemGuardian = ActorSystem.create(WalletApp.create(10), "walletApp");
        systemGuardian.tell(10);
        System.out.println("Press ENTER to terminate");
        System.in.read();
        systemGuardian.terminate();

    }
    public WalletApp(ActorContext<Integer> context, int credit) {
        super(context);
        this.credit = credit;
    }

    public static Behavior<Integer> create(int credit) {
        return Behaviors.setup(context -> new WalletApp(context, credit));
    }

    @Override
    public Receive<Integer> createReceive() {
        return newReceiveBuilder().onMessage(Integer.class, this::showCredit)
                .build();
    }

    private Behavior<Integer> showCredit(Integer newCredit){
        this.credit+=newCredit;
        getContext().getLog().debug("wallet new credit {}",credit);
        return Behaviors.same();
    }
}
