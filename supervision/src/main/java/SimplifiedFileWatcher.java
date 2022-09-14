import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.ChildFailed;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;

public class SimplifiedFileWatcher {

    public static Behavior<Command> create() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof Watch watch) {
                context.watch(watch.ref());
                return Behaviors.same();
            } else {
                return Behaviors.unhandled();
            }
        }, (context, signal) -> {
            if (signal instanceof ChildFailed) {
                context.getLog().info("childFailed");
                return Behaviors.same();
            } else if (signal instanceof Terminated) {
                context.getLog().info("terminated");
                return Behaviors.same();
            } else {
                return Behaviors.unhandled();
            }
        });
    }

    public sealed interface Command {
    }

    public record Watch(ActorRef<String> ref) implements Command {
    }
}
