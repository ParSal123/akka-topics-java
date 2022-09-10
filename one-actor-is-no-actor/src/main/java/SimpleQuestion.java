package ask;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class SimpleQuestion {
    public static void main(String[] args) {
        ActorSystem<Guardian.Command> guardian = ActorSystem.create(Guardian.create(), "example-ask-without-content");
        guardian.tell(new Guardian.Start(new ArrayList<>(Arrays.asList("text-a", "text-b", "text-c"))));
    }
}

class Guardian {
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            ActorRef<Manager.Command> manager = context.spawn(Manager.create(), "manager-1");
            return Behaviors.receive(Command.class).onMessage(Start.class, start -> {
                manager.tell(new Manager.Delegate(start.texts));
                return Behaviors.same();
            }).build();
        });
    }

    public sealed interface Command {
    }

    public record Start(ArrayList<String> texts) implements Command {
    }

}

class Manager {
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Delegate.class, delegate -> {
            delegate.texts().forEach(text -> {
                ActorRef<Worker.Command> worker = context.spawn(Worker.create(text), String.format("worker-%s", text));
                context.ask(Worker.Response.class, worker, Duration.ofSeconds(3), Worker.Parse::new, (response, throwable) -> {
                    if (throwable == null) {
                        return new Report(String.format("%s read by %s", text, worker.path().name()));
                    } else {
                        return new Report(String.format("reading %s has failed with [%s]", text, throwable.getMessage()));
                    }
                });
            });
            return Behaviors.same();
        }).onMessage(Report.class, report -> {
            context.getLog().info(report.description);
            return Behaviors.same();
        }).build());
    }

    public sealed interface Command {
    }

    public record Delegate(ArrayList<String> texts) implements Command {
    }

    private record Report(String description) implements Command {
    }
}

class Worker {

    public static Behavior<Command> create(String text) {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Parse.class, parse -> {
            fakeLenghtyParsing(text);
            prettyPrint(context, "DONE!");
            parse.replyTo.tell(new Done());
            return Behaviors.same();
        }).build());
    }

    public static void fakeLenghtyParsing(String text) {
        var endTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(2000, 4000);
        while (System.currentTimeMillis() < endTime) {
            // do nothing
        }
    }

    public static void prettyPrint(ActorContext<?> context, String message) {
        context.getLog().info("{}: {}", context.getSelf().path().name(), message);
    }

    public sealed interface Command {
    }

    public sealed interface Response {
    }

    public record Parse(ActorRef<Response> replyTo) implements Command {
    }

    public static final class Done implements Response {
    }
}