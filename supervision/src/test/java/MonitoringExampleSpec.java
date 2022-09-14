import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

class MonitoringExampleSpec {
    static final ActorTestKit testKit = ActorTestKit.create();
    static final Behavior<String> restartingChildBehavior = Behaviors.supervise(ParentWatcher.childBehavior).onFailure(SupervisorStrategy.restart());

    @Test
    void TwoSiblingActorsWatcherMustBeNotifiedWhenOtherOneStops() {
        var watcher = testKit.spawn(SimplifiedFileWatcher.create());
        var logProcessor = testKit.spawn(Behaviors.receiveMessage((String message) ->
                switch (message) {
                    case "stop" -> Behaviors.stopped();
                    default -> Behaviors.unhandled();
                }
        ));

        watcher.tell(new SimplifiedFileWatcher.Watch(logProcessor));

        Supplier<Void> tellStopToLogProcessor = () -> {
            logProcessor.tell("stop");
            return null;
        };

        LoggingTestKit.info("terminated").expect(testKit.system(), tellStopToLogProcessor);
    }

    @Test
    void TwoSiblingActorsWatcherMustGetTerminatedSignalWhenOtherOneFails() {
        var watcher = testKit.spawn(SimplifiedFileWatcher.create());
        var behavior = testKit.spawn(Behaviors.receiveMessage((String message) ->
                switch (message) {
                    case "exception" -> throw new IllegalStateException();
                    default -> Behaviors.unhandled();
                }
        ));

        watcher.tell(new SimplifiedFileWatcher.Watch(behavior));

        Supplier<Void> tellExceptionToBehavior = () -> {
            behavior.tell("exception");
            return null;
        };

        LoggingTestKit.info("terminated").expect(testKit.system(), tellExceptionToBehavior);
    }

    @Test
    void ParentWatcherMustGetChildFailedSignalWhenChildFails() {
        var probe = testKit.createTestProbe(String.class);
        var watcher = testKit.spawn(ParentWatcher.create(probe.ref(), List.of()));
        watcher.tell(new ParentWatcher.Spawn(ParentWatcher.childBehavior));
        watcher.tell(new ParentWatcher.FailChildren());
        probe.expectMessage("childFailed");
    }

    @Test
    void ParentWatcherMustGetTerminatedSignalWhenChildStops() {
        var probe = testKit.createTestProbe(String.class);
        var watcher = testKit.spawn(ParentWatcher.create(probe.ref(), new ArrayList<>()));
        watcher.tell(new ParentWatcher.Spawn(ParentWatcher.childBehavior));
        watcher.tell(new ParentWatcher.StopChildren());
        probe.expectMessage("terminated");
    }

    @Test
    void ParentWatcherMustNotBeNotifiedWhenChildThrowsNonFatalExceptionWithRestartStrategy() {
        var probe = testKit.createTestProbe(String.class);
        var watcher = testKit.spawn(ParentWatcher.create(probe.ref(), List.of()));
        watcher.tell(new ParentWatcher.Spawn(restartingChildBehavior));
        watcher.tell(new ParentWatcher.FailChildren());
        probe.expectNoMessage();
    }

    @Test
    void ParentWatcherShouldBeNotifiedIfChildWithRestartStrategyIsStopped() {
        var probe = testKit.createTestProbe(String.class);
        var watcher = testKit.spawn(ParentWatcher.create(probe.ref(), List.of()));
        watcher.tell(new ParentWatcher.Spawn(restartingChildBehavior));
        watcher.tell(new ParentWatcher.StopChildren());
        probe.expectMessage("terminated");
    }
}
