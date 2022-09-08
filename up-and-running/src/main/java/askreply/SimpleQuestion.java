package askreply;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

class SimpleQuestion {
    public static void main(String[] args) {
        ActorSystem<Guardian.Command> guardian = ActorSystem.create(Guardian.create(), "example-ask-without-content");
        guardian.tell(new Guardian.Start(new String[]{"text-a", "text-b", "text-c"}));
    }
}

class Guardian {
    static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            ActorRef<Manager.Command> manager = context.spawn(Manager.create(), "manager");
            return Behaviors.receive(Command.class).onMessage(Start.class, start -> {
                manager.tell(new Manager.Delegate(start.texts));
                return Behaviors.same();
            }).build();
        });
    }

    sealed interface Command {
    }

    record Start(String[] texts) implements Command {
    }

}

class Manager {
    static Behavior<Command> create() {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Delegate.class, delegate -> {
            for (String text : delegate.texts) {
                ActorRef<Worker.Command> worker = context.spawn(Worker.create(text), String.format("worker-%s", text));
                context.ask(Worker.Response.class, worker, Duration.ofSeconds(3), replyTo -> new Worker.Parse(replyTo), (response, throwable) -> {
                    if (throwable == null) {
                        return new Report(String.format("%s read by %s", text, worker.path().name()));
                    } else {
                        return new Report(String.format("reading %s has failed with [%s]", text, throwable.getMessage()));
                    }
                });
            }
            return Behaviors.same();
        }).onMessage(Report.class, report -> {
            context.getLog().info(report.description);
            return Behaviors.same();
        }).build());
    }

    sealed interface Command {
    }

    record Delegate(String[] texts) implements Command {
    }

    private record Report(String description) implements Command {
    }
}

class Worker {

    static Behavior<Command> create(String text) {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Parse.class, parse -> {
            fakeLenghtyParsing(text);
            prettyParse(context, text);
            parse.replyTo.tell(new Done());
            return Behaviors.same();
        }).build());
    }

    static void fakeLenghtyParsing(String text) {
        var endTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(2000, 4000);
        while (System.currentTimeMillis() < endTime) {
            // do nothing
        }
    }

    static void prettyParse(ActorContext<?> context, String message) {
        context.getLog().info("{}: {}", context.getSelf().path().name(), message);
    }

    sealed interface Command {
    }

    sealed interface Response {
    }

    record Parse(ActorRef<Response> replyTo) implements Command {
    }

    static final class Done implements Response {
    }
}