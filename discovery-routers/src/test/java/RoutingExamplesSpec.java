import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RoutingExamplesSpec {
  public static ActorTestKit testKit = ActorTestKit.create();

  @Test
  void PoolRouterShouldSendMessagesInRoundRobinFashion() {
    var probe = testKit.<String>createTestProbe();
    var worker = Worker.create(probe.ref());
    var router = testKit.spawn(Manager.create(worker), "round-robin");

    probe.expectMessage("hi");
    probe.receiveSeveralMessages(10);
  }

  @Test
  void PoolRouterShouldBroadcastToAllRoutees() {
    var probe = testKit.<String>createTestProbe();
    var worker = Worker.create(probe.ref());
    var router = testKit.spawn(BroadcastingManager.create(worker), "broadcasting");

    probe.expectMessage("hi, there");
    probe.receiveSeveralMessages(43);
  }

  @Test
  void PoolRouterShouldSendMessagesToOneActorWithConstantHashing() {
    var probe = testKit.<String>createTestProbe();
    var worker = Worker.create(probe.ref());
    var router = testKit.spawn(Manager.create(worker), "constant-hashing");

    probe.expectMessage("hi");
    probe.receiveSeveralMessages(10);
  }

  @Test
  @Disabled("For demonstration purposes only")
  void GroupRouterShouldSendMessagesToOneWorkerRegisteredAtAKey() {
    var probe1 = testKit.<String>createTestProbe();
    var behavior1 = Behaviors.monitor(String.class, probe1.ref(), Behaviors.empty());

    testKit
        .system()
        .receptionist()
        .tell(Receptionist.register(PhotoProcessor.Key, testKit.spawn(behavior1)));

    var groupRouter = testKit.spawn(Camera.create());
    groupRouter.tell(new Camera.Photo("hi"));

    probe1.expectMessage("hi");
    // This is wrong! "hi" is already received. expectMessage pops one message from mailbox.
    // So there's no other message left.
    // probe1.receiveSeveralMessages(1);
  }

  @Test
  void GroupRouterShouldSendMessagesToAllRegisteredProcessors() {
    var photoProcessor1 = testKit.<String>createTestProbe();
    var pp1Monitor =
        Behaviors.monitor(String.class, photoProcessor1.ref(), PhotoProcessor.create());

    var photoProcessor2 = testKit.<String>createTestProbe();
    var pp2Monitor =
        Behaviors.monitor(String.class, photoProcessor2.ref(), PhotoProcessor.create());

    testKit
        .system()
        .receptionist()
        .tell(Receptionist.register(PhotoProcessor.Key, testKit.spawn(pp1Monitor)));
    testKit
        .system()
        .receptionist()
        .tell(Receptionist.register(PhotoProcessor.Key, testKit.spawn(pp2Monitor)));

    var camera = testKit.spawn(Camera.create());
    camera.tell(new Camera.Photo("A"));
    camera.tell(new Camera.Photo("B"));

    photoProcessor1.receiveSeveralMessages(1);
    photoProcessor2.receiveSeveralMessages(1);
  }

  @Test
  void GroupRouterShouldSendMessagesWithSameIdToSameAggregator() {
    var probe1 = testKit.<Aggregator.Event>createTestProbe();
    var probe2 = testKit.<Aggregator.Event>createTestProbe();

    testKit.spawn(Aggregator.create(new HashMap<>(), probe1.ref()), "aggregator1");
    testKit.spawn(Aggregator.create(new HashMap<>(), probe2.ref()), "aggregator2");

    var contentValidator = testKit.spawn(DataObfuscator.create(), "wa-1");
    var dataEnricher = testKit.spawn(DataEnricher.create(), "wb-1");

    contentValidator.tell(new DataObfuscator.Message("123", "Text"));
    dataEnricher.tell(new DataEnricher.Message("123", "Text"));

    probe1.expectMessage(new Aggregator.Completed("123", "text", "metadata"));
    probe2.expectNoMessage();

    contentValidator.tell(new DataObfuscator.Message("z23", "LoreIpsum"));
    dataEnricher.tell(new DataEnricher.Message("z23", "LoreIpsum"));

    probe1.expectNoMessage();
    probe2.expectMessage(new Aggregator.Completed("z23", "loreipsum", "metadata"));
  }

  @Test
  void StateRouterShouldRouteToForwardToActorReferenceWhenOn() {
    var forwardToProbe = testKit.<String>createTestProbe();
    var alertToProbe = testKit.<String>createTestProbe();
    var switchh = testKit.spawn(Switch.create(forwardToProbe.ref(), alertToProbe.ref()), "switch");
    switchh.tell(new Switch.Payload("content1", "metadata1"));
    forwardToProbe.expectMessage("content1");
  }

  @Test
  void StateRouterShouldRouteToAlertActorAndWaitWhenOff() {
    var forwardToProbe = testKit.<String>createTestProbe();
    var alertToProbe = testKit.<String>createTestProbe();
    var switchh = testKit.spawn(Switch.create(forwardToProbe.ref(), alertToProbe.ref()), "switch2");
    switchh.tell(Switch.SwitchOff.INSTANCE);
    switchh.tell(new Switch.Payload("content2", "metadata2"));
    alertToProbe.expectMessage("content2");
  }
}

class Worker {
  public static Behavior<String> create(ActorRef<String> monitor) {
    return Behaviors.receiveMessage(
        msg -> {
          monitor.tell(msg);
          return Behaviors.same();
        });
  }
}

class Manager {
  public static Behavior<Void> create(Behavior<String> behavior) {
    return Behaviors.setup(
        context -> {
          var routingBehavior = Routers.pool(4, behavior);
          var router = context.spawn(routingBehavior, "test-pool");
          for (int i = 0; i <= 10; i++) {
            router.tell("hi");
          }
          return Behaviors.empty();
        });
  }
}

class BroadcastingManager {
  public static Behavior<Void> create(Behavior<String> behavior) {
    return Behaviors.setup(
        context -> {
          var poolSize = 4;
          var routingBehavior =
              Routers.pool(poolSize, behavior).withBroadcastPredicate(msg -> msg.length() > 5);
          var router = context.spawn(routingBehavior, "test-pool");
          for (int i = 0; i <= 10; i++) {
            router.tell("hi, there");
          }
          return Behaviors.empty();
        });
  }
}

class PhotoProcessor {
  public static ServiceKey<String> Key = ServiceKey.create(String.class, "photo-processor-key");

  public static Behavior<String> create() {
    return Behaviors.ignore();
  }
}

class Camera {
  public static Behavior<Photo> create() {
    return Behaviors.setup(
        context -> {
          var routingBehavior = Routers.group(PhotoProcessor.Key).withRoundRobinRouting();
          var router = context.spawn(routingBehavior, "photo-processor-pool");

          return Behaviors.receiveMessage(
              photo -> {
                router.tell(photo.content);
                return Behaviors.same();
              });
        });
  }

  public record Photo(String content) {}
}

class Aggregator {
  public static ServiceKey<Command> serviceKey = ServiceKey.create(Command.class, "agg-key");

  public static String mapping(Command command) {
    return command.id();
  }

  public static Behavior<Command> create(Map<String, String> messages, ActorRef<Event> forwardTo) {
    return Behaviors.setup(
        context -> {
          context
              .getSystem()
              .receptionist()
              .tell(Receptionist.register(serviceKey, context.getSelf()));

          return Behaviors.receiveMessage(
              msg -> {
                if (msg instanceof Obfuscated obfuscated) {
                  var metadata = messages.get(obfuscated.id());
                  if (metadata != null) {
                    forwardTo.tell(new Completed(obfuscated.id(), obfuscated.content(), metadata));
                    messages.remove(obfuscated.id());
                  } else {
                    messages.put(obfuscated.id(), obfuscated.content());
                  }
                  return create(messages, forwardTo);
                } else if (msg instanceof Enriched enriched) {
                  var content = messages.get(enriched.id());
                  if (content != null) {
                    forwardTo.tell(new Completed(enriched.id(), content, enriched.metadata()));
                    messages.remove(enriched.id());
                  } else {
                    messages.put(enriched.id(), enriched.metadata());
                  }
                  return create(messages, forwardTo);
                } else {
                  return Behaviors.unhandled();
                }
              });
        });
  }

  public sealed interface Command {
    String id();
  }

  public sealed interface Event {}

  public record Obfuscated(String id, String content) implements Command {}

  public record Enriched(String id, String metadata) implements Command {}

  public record Completed(String id, String content, String metadata) implements Event {}
}

class DataObfuscator {
  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          var router =
              context.spawnAnonymous(
                  Routers.group(Aggregator.serviceKey)
                      .withConsistentHashingRouting(10, Aggregator::mapping));

          return Behaviors.receiveMessage(
              msg -> {
                if (msg instanceof Message message) {
                  router.tell(
                      new Aggregator.Obfuscated(message.id(), message.content().toLowerCase()));
                }
                return Behaviors.same();
              });
        });
  }

  public sealed interface Command {}

  public record Message(String id, String content) implements Command {}
}

class DataEnricher {
  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          var router =
              context.spawnAnonymous(
                  Routers.group(Aggregator.serviceKey)
                      .withConsistentHashingRouting(10, Aggregator::mapping));

          return Behaviors.receiveMessage(
              msg -> {
                if (msg instanceof Message message) {
                  router.tell(new Aggregator.Enriched(message.id(), "metadata"));
                }
                return Behaviors.same();
              });
        });
  }

  public sealed interface Command {}

  public record Message(String id, String content) implements Command {}
}

class Switch {
  public static Behavior<Command> create(ActorRef<String> forwardTo, ActorRef<String> alertTo) {
    return on(forwardTo, alertTo);
  }

  public static Behavior<Command> on(ActorRef<String> forwardTo, ActorRef<String> alertTo) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof SwitchOn) {
            context.getLog().warn("sent SwitchOn but was ON already");
            return Behaviors.same();
          } else if (message instanceof SwitchOff) {
            return off(forwardTo, alertTo);
          } else if (message instanceof Payload payload) {
            forwardTo.tell(payload.content());
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        });
  }

  private static Behavior<Command> off(ActorRef<String> forwardTo, ActorRef<String> alertTo) {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof SwitchOn) {
            return on(forwardTo, alertTo);
          } else if (message instanceof SwitchOff) {
            context.getLog().warn("sent SwitchOff but was OFF already");
            return Behaviors.same();
          } else if (message instanceof Payload payload) {
            alertTo.tell(payload.content());
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        });
  }

  public enum SwitchOn implements Command {
    INSTANCE
  }

  public enum SwitchOff implements Command {
    INSTANCE
  }

  public sealed interface Command {}

  public record Payload(String content, String metadata) implements Command {}
}
