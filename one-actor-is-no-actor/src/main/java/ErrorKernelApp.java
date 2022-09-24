package errorkernel;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.BehaviorBuilder;
import akka.actor.typed.javadsl.Behaviors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class ErrorKernelApp {

  public static void main(String[] args) throws IOException {
    ActorSystem<Guardian.Command> guardian = ActorSystem.create(Guardian.create(), "error-kernel");
    guardian.tell(new Guardian.Start(new ArrayList<>(Arrays.asList("-one-", "--two--"))));

    System.out.println("Press ENTER to terminate");
    System.in.read();
    guardian.terminate();
  }
}

class Guardian {

  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          context.getLog().info("setting up. Creating manager");
          ActorRef<Manager.Command> manager = context.spawn(Manager.create(), "manager-alpha");
          return Behaviors.receiveMessage(
              msg -> {
                if (msg instanceof Start start) {
                  manager.tell(new Manager.Delegate(start.texts));
                  return Behaviors.same();
                } else return Behaviors.unhandled();
              });
          /* Can also be written like this:
                 return BehaviorBuilder.<Command>create()
                         .onMessage(
                                 Guardian.Start.class,
                                 start -> {
                                     manager.tell(new Manager.Delegate(start.texts));
                                     return Behaviors.same();
                                 })
                         .build();

          */
        });
  }

  public interface Command {}

  public record Start(ArrayList<String> texts) implements Command {}
}

class Manager {

  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          ActorRef<Worker.Response> adapter =
              context.messageAdapter(Worker.Response.class, WorkerDoneAdapter::new);
          return BehaviorBuilder.<Command>create()
              .onMessage(
                  Delegate.class,
                  delegate -> {
                    delegate.texts.forEach(
                        text -> {
                          ActorRef<Worker.Command> worker = context.spawn(Worker.create(), text);
                          context.getLog().info("sending text '{}' to worker", text);
                          worker.tell(new Worker.Parse(adapter, text));
                        });
                    return Behaviors.same();
                  })
              .onMessage(
                  WorkerDoneAdapter.class,
                  workerDone -> {
                    context
                        .getLog()
                        .info(
                            "text '{}' has been finished",
                            ((Worker.Done) (workerDone.response)).text());
                    return Behaviors.same();
                  })
              .build();
        });
  }

  public sealed interface Command {}

  public record Delegate(ArrayList<String> texts) implements Command {}

  private record WorkerDoneAdapter(Worker.Response response) implements Command {}
}

class Worker {

  public static Behavior<Command> create() {
    return Behaviors.setup(
        context ->
            BehaviorBuilder.<Command>create()
                .onMessage(
                    Parse.class,
                    parse -> {
                      String parsed = naiveParsing(parse.text);
                      context
                          .getLog()
                          .info("'{}' DONE!. Parsed result: {}", context.getSelf(), parsed);
                      parse.replyTo.tell(new Done(parse.text));
                      return Behaviors.stopped();
                    })
                .build());
  }

  public static String naiveParsing(String text) {
    return text.replace("-", "");
  }

  public interface Command {}

  public sealed interface Response {}

  public record Parse(ActorRef<Response> replyTo, String text) implements Command {}

  public record Done(String text) implements Response {}
}
