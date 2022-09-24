package fishing;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.FishingOutcomes;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FishingSpec {

  static ActorTestKit testKit = ActorTestKit.create();

  @Test
  void TimingTestMustBeAbleToCancelTimer() {
    var probe = testKit.createTestProbe(Receiver.Command.class);
    var timerKey = "key1234";
    var interval = java.time.Duration.ofSeconds(1);

    var sender =
        Behaviors.<Sender.Command>withTimers(
            timer -> {
              timer.startTimerAtFixedRate(timerKey, new Sender.Tick(), interval);
              return Sender.create(probe.getRef(), timer);
            });

    var ref = testKit.spawn(sender);
    probe.expectMessageClass(Receiver.Tock.class);
    probe.fishForMessage(
        java.time.Duration.ofSeconds(3),
        msg -> {
          if (msg instanceof Receiver.Tock) {
            if (new Random().nextInt(4) == 0) {
              ref.tell(new Sender.Cancel(timerKey));
            }
            return FishingOutcomes.continueAndIgnore();
          } else if (msg instanceof Receiver.Cancelled) {
            return FishingOutcomes.complete();
          } else {
            return FishingOutcomes.fail("unexpected message: " + msg);
          }
        });
    probe.expectNoMessage(interval.plus(java.time.Duration.ofMillis(100)));
  }

  @Test
  void MonitorMustInterceptTheMessages() {
    var probe = testKit.createTestProbe(String.class);
    var behavior = Behaviors.<String>receiveMessage(notUsed -> Behaviors.ignore());
    var behaviorMonitored = Behaviors.monitor(String.class, probe.getRef(), behavior);
    var actor = testKit.spawn(behaviorMonitored);

    actor.tell("checking");
    probe.expectMessage("checking");
  }

  @Test
  void AutomatedResumingCounterMustReceiveResumeAfterPause() {
    var probe = testKit.createTestProbe(CounterTimer.Command.class);
    var counterMonitored =
        Behaviors.monitor(CounterTimer.Command.class, probe.getRef(), CounterTimer.create());
    var counter = testKit.spawn(counterMonitored);

    counter.tell(new CounterTimer.Pause(1));
    probe.fishForMessage(
        java.time.Duration.ofSeconds(3),
        msg -> {
          if (msg instanceof CounterTimer.Increase) {
            return FishingOutcomes.continueAndIgnore();
          } else if (msg instanceof CounterTimer.Pause) {
            return FishingOutcomes.continueAndIgnore();
          } else {
            return FishingOutcomes.complete();
          }
        });
  }
}

class Receiver {

  public static Behavior<Command> create() {
    return Behaviors.ignore();
  }

  public sealed interface Command {}

  public static final class Tock implements Command {}

  public static final class Cancelled implements Command {}
}

class Sender {

  public static Behavior<Command> create(
      ActorRef<Receiver.Command> forwardTo, TimerScheduler<Command> timer) {
    return Behaviors.receiveMessage(
        cmd -> {
          if (cmd instanceof Tick) {
            forwardTo.tell(new Receiver.Tock());
            return Behaviors.same();
          } else if (cmd instanceof Cancel cancel) {
            timer.cancel(cancel.key());
            forwardTo.tell(new Receiver.Cancelled());
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        });
  }

  public sealed interface Command {}

  public static final class Tick implements Command {}

  public record Cancel(String key) implements Command {}
}
