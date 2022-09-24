package style.object_oriented;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.IOException;

public class WalletApp extends AbstractBehavior<Integer> {

    public static void main(String[] args) throws IOException {
        final ActorSystem<Integer> guardian = ActorSystem.create(WalletApp.create(), "hello-world");
        guardian.tell(1);
        guardian.tell(10);
        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();

    }
    public WalletApp(ActorContext<Integer> context) {
        super(context);
    }

    public static Behavior<Integer> create() {
        return Behaviors.setup(WalletApp::new);
    }

    @Override
    public Receive<Integer> createReceive() {
        return newReceiveBuilder().onMessage(Integer.class, this::showCredit)
                .build();
    }

    private Behavior<Integer> showCredit(Integer message){
        getContext().getLog().info("received '{}' dollar(s)", message);
        return this;
    }
}
