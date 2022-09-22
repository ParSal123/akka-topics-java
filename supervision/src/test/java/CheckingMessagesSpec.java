import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class CheckingMessagesSpec {
  static final ActorTestKit testKit = ActorTestKit.create();

  static final String errorMessage = "about to fail with: 2";

  static Behavior<Integer> beh(ActorRef<Integer> monitor) {
    return Behaviors.supervise(
            Behaviors.receive(
                (ActorContext<Integer> context, Integer message) -> {
                  if (message == 2) {
                    monitor.tell(2);
                    throw new IllegalArgumentException("2");
                  } else return Behaviors.same();
                }))
        .onFailure(SupervisorStrategy.restart());
  }

  @AfterAll
  static void tearDown() {
    testKit.shutdownTestKit();
  }

  @Test
  void ActorThatRestartsDoesntReprocessTheFailingMessage() {
    var probe = testKit.createTestProbe(Integer.class);
    var actor = testKit.spawn(beh(probe.ref()));
    for (int i = 1; i <= 10; i++) actor.tell(i);
    probe.expectMessage(2);
    probe.expectNoMessage();
  }
}
