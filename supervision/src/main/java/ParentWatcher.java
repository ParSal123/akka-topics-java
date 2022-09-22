import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.ChildFailed;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import java.util.ArrayList;
import java.util.List;

class ParentWatcher {
  static Behavior<String> childBehavior =
      Behaviors.receiveMessage(
          message ->
              switch (message) {
                case "stop" -> Behaviors.stopped();
                case "exception" -> throw new Exception();
                case "error" -> throw new OutOfMemoryError();
                default -> Behaviors.unhandled();
              });

  public static Behavior<Command> create(
      ActorRef<String> monitor, List<ActorRef<String>> children) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof Spawn spawn) {
            var child = context.spawnAnonymous(spawn.behavior);
            context.watch(child);
            var newChildren = new ArrayList<>(children);
            newChildren.add(child);
            return create(monitor, newChildren);
          } else if (message instanceof StopChildren) {
            children.forEach(child -> child.tell("stop"));
            return Behaviors.same();
          } else if (message instanceof FailChildren) {
            children.forEach(child -> child.tell("exception"));
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        },
        (context, signal) -> {
          if (signal instanceof ChildFailed) {
            monitor.tell("childFailed");
            return Behaviors.same();
          } else if (signal instanceof Terminated) {
            monitor.tell("terminated");
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public record Spawn(Behavior<String> behavior) implements Command {}

  public static final class StopChildren implements Command {}

  public static final class FailChildren implements Command {}
}
