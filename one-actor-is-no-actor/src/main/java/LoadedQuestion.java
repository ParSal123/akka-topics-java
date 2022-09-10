package ask.content;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

class LoadedQuestion {
    public static void main(String[] args) {
        ActorSystem<Guardian.Command> guardian = ActorSystem.create(Guardian.create(), "LoadedQuestion");
        guardian.tell(new Guardian.Start());
    }
}

class Guardian {
    static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            ActorRef<Manager.Command> manager = context.spawn(Manager.create(), "manager-1");
            return Behaviors.receive(Command.class).onMessage(Start.class, start -> {
                manager.tell(new Manager.Delegate(new String[]{"file-a", "file-b", "file-c"}));
                return Behaviors.same();
            }).build();
        });
    }

    sealed interface Command {
    }

    static final class Start implements Command {
    }

}

class Manager {
    static Behavior<Command> create() {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Delegate.class, delegate -> {
            for (String file : delegate.files) {
                ActorRef<ask.content.Reader.Command> reader = context.spawn(ask.content.Reader.create(), String.format("reader-%s", file));
                context.ask(ask.content.Reader.Response.class, reader, Duration.ofSeconds(3), replyTo -> new Reader.Read(file, replyTo), (response, throwable) -> {
                    if (throwable == null) {
                        return new Report(String.format("%s read by %s", file, reader.path().name()));
                    } else {
                        return new Report(String.format("reading '%s' has failed with [%s]", file, throwable.getMessage()));
                    }
                });
            }
            return Behaviors.same();
        }).onMessage(Report.class, report -> {
            context.getLog().info(report.outline);
            return Behaviors.same();
        }).build());
    }

    sealed interface Command {
    }

    record Delegate(String[] files) implements Command {
    }

    private record Report(String outline) implements Command {
    }
}

class Reader {

    static Behavior<Command> create() {
        return Behaviors.setup(context -> Behaviors.receive(Command.class).onMessage(Read.class, read -> {
            fakeReading(read.file);
            prettyPrint(context, read.file + " done"); // to show that it's done when delay
            read.replyTo.tell(new Done());
            return Behaviors.same();
        }).build());
    }

    static void fakeReading(String file) {
        var endTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(2000, 4000);
        while (System.currentTimeMillis() < endTime) {
            // do nothing
        }
    }

    static void prettyPrint(ActorContext<?> context, String message) {
        context.getLog().info("{}: {}", context.getSelf().path().name(), message);
    }

    sealed interface Command {
    }

    sealed interface Response {
    }

    record Read(String file, ActorRef<Response> replyTo) implements Command {
    }

    static final class Done implements Response {
    }
}