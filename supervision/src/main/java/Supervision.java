import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

class SupervisionExample {

    private SupervisionExample() {
    }

    public static void cleaning(ActorContext<String> context, String info) {
        context.getLog().info(info);
    }

    public static Behavior<String> create() {
        /* receivePartial function is not available in java.
         * You need to explicitly handle the cases then add a default clause
         * for Behaviors.unhandled().*/
        return Behaviors.receive((context, message) -> {
            switch (message) {
                case "secret":
                    context.getLog().info("granted");
                    return Behaviors.same();
                case "stop":
                    context.getLog().info("stopping");
                    return Behaviors.stopped();
                case "recoverable":
                    context.getLog().info("recoverable");
                    throw new IllegalStateException();
                case "fatal":
                    throw new OutOfMemoryError();
                default:
                    return Behaviors.unhandled();
            }
        }, (context, signal) -> {
            if (signal instanceof PostStop) {
                cleaning(context, "cleaning resources");
            }
            return Behaviors.same();
        });
    }
}
