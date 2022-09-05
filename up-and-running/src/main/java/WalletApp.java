import java.io.IOException;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

class WalletApp {

    public static void main(String[] args) throws IOException {
        ActorSystem<Integer> guardian = ActorSystem.create(createWallet(), "hello-world");
        guardian.tell(1);
        guardian.tell(10);

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }

    static Behavior<Integer> createWallet() {
        return Behaviors.receive((context, message) -> {
            context.getLog().info("received '{}' dollar(s)", message);
            return Behaviors.same();
        });
    }

}
