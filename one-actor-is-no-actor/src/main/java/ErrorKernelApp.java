
import java.io.IOException;
import java.util.stream.Stream;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.BehaviorBuilder;
import akka.actor.typed.javadsl.Behaviors;

public class ErrorKernelApp {

    public static void main(String[] args) throws IOException {
        ActorSystem<Guardian.Command> guardian = ActorSystem.create(Guardian.create(), "error-kernel");
        guardian.tell(new Guardian.Start(new String[]{"-one-", "--two--"}));

        System.out.println("Press ENTER to terminate");
        System.in.read();
        guardian.terminate();
    }
}

class Guardian {

    interface Command {
    }

//    static final class Start implements Command {
//        String[] texts;
//
//        Start(String[] texts) {
//            this.texts = texts;
//        }
//    }

    record Start(String[] texts) implements Command {
    }


    static Behavior<Command> create() {
        return Behaviors.setup(
                context -> {
                    context.getLog().info("setting up. Creating manager");
                    ActorRef<Manager.Command> manager = context.spawn(Manager.create(), "manager-alpha");
                    return Behaviors.receiveMessage(
                            msg -> {
                                if (msg instanceof Start start) {
                                    context.getLog().info("starting");
                                    manager.tell(new Manager.Delegate(start.texts));
                                    return Behaviors.same();
                                } else
                                    return Behaviors.unhandled();
                            });
//                    return BehaviorBuilder.<Command>create()
//                            .onMessage(
//                                    Guardian.Start.class,
//                                    start -> {
//                                        manager.tell(new Manager.Delegate(start.texts));
//                                        return Behaviors.same();
//                                    })
//                            .build();
                });
    }
}

class Manager {

    interface Command {
    }

//    static final class Delegate implements Command {
//        String[] texts;
//
//        Delegate(String[] texts) {
//            this.texts = texts;
//        }
//    }

    record Delegate(String[] texts) implements Command {
    }

    private record WorkerDoneAdapter(Worker.Response response) implements Command {
    }

    static Behavior<Command> create() {
        return Behaviors.setup(
                context -> {
                    context.getLog().info("setting up. Creating worker");
                    ActorRef<Worker.Response> adapter =
                            context.messageAdapter(Worker.Response.class, WorkerDoneAdapter::new);
                    return BehaviorBuilder.<Command>create()
                            .onMessage(
                                    Delegate.class,
                                    delegate -> {
                                        Stream.of(delegate.texts)
                                                .forEach(
                                                        text -> {
                                                            ActorRef<Worker.Command> worker =
                                                                    context.spawn(Worker.create(), text);
                                                            context.getLog().info("sending text '{}' to worker", text);
                                                            worker.tell(new Worker.Parse(adapter, text));
                                                        });
                                        return Behaviors.same();
                                    })
                            .onMessage(
                                    WorkerDoneAdapter.class,
                                    workerDone -> {
                                        context.getLog().info("text '{}' has been finished.", workerDone.response);
                                        return Behaviors.same();
                                    })
                            .build();
                });
    }
}

class Worker {

    interface Command {
    }

//    static final class Parse implements Command {
//        ActorRef<Response> replyTo;
//        String text;
//
//        Parse(ActorRef<Response> replyTo, String text) {
//            this.replyTo = replyTo;
//            this.text = text;
//        }
//    }

    record Parse(ActorRef<Response> replyTo, String text) implements Command {
    }

    interface Response {
    }

//    static final class Done implements Response {
//        String text;
//
//        Done(String text) {
//            this.text = text;
//        }
//    }

    record Done(String text) implements Response {
    }

    static Behavior<Command> create() {
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
                                            parse.replyTo.tell(new Done(parsed));
                                            return Behaviors.stopped();
                                        })
                                .build());
    }

    static String naiveParsing(String text) {
        return text.replaceAll("-", "");
    }
}
