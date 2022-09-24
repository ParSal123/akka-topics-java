import static org.junit.jupiter.api.Assertions.assertThrows;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ReceptionUsageSpec {

  static final ActorTestKit testKit = ActorTestKit.create();

  @AfterAll
  static void tearDown() {
    testKit.shutdownTestKit();
  }

  @Test
  void ActorSubscribedToServiceKeyShouldGetNotifiedWhenActorsRegister() {
    var wick = testKit.spawn(VIPGuest.create(), "Mr.Wick");
    testKit.spawn(HotelConcierge.create());
    Supplier<Void> wickEnterHotel =
        () -> {
          wick.tell(VIPGuest.EnterHotel.INSTANCE);
          return null;
        };
    LoggingTestKit.info("Mr.Wick is in").expect(testKit.system(), wickEnterHotel);
    var guest2 = testKit.spawn(VIPGuest.create(), "Mr.Ious");
    Supplier<Void> guest2EnterHotel =
        () -> {
          guest2.tell(VIPGuest.EnterHotel.INSTANCE);
          return null;
        };
    LoggingTestKit.info("Mr.Ious is in").expect(testKit.system(), guest2EnterHotel);
  }

  @Test
  void ActorSubscribedToServiceKeyShouldFindARegisteredActor() {
    var wick = testKit.spawn(VIPGuest.create(), "Mr.Wick");
    wick.tell(VIPGuest.EnterHotel.INSTANCE);
    var testProbe = testKit.<ActorRef<VIPGuest.Command>>createTestProbe();
    var finder = testKit.spawn(GuestSearch.create("Mr.Wick", testProbe.ref()), "searcher");
    finder.tell(GuestSearch.Find.INSTANCE);
    // Unchecked method invocation.
    // Java doesn't have a generic equivalent of Scala's `ActorRef[VIPGuest.Command]`
    // See this link for more info:
    // https://stackoverflow.com/questions/2770321/what-is-a-raw-type-and-why-shouldnt-we-use-it
    testProbe.expectMessageClass(ActorRef.class);
  }

  @Test
  void ActorSubscribedToServiceKeyShouldNotFindAnUnregisteredActor() {
    var testProbe = testKit.<ActorRef<VIPGuest.Command>>createTestProbe();
    var finder = testKit.spawn(GuestSearch.create("NoOne", testProbe.ref()), "searcher2");
    finder.tell(GuestSearch.Find.INSTANCE);
    testProbe.expectNoMessage();
  }

  @Test
  void ActorSubscribedToServiceKeyShouldFindRegisteredActorsUsingSearchParamsInFind() {
    var wick = testKit.spawn(VIPGuest.create(), "Mr.Wick");
    wick.tell(VIPGuest.EnterHotel.INSTANCE);
    var probe = testKit.<ActorRef<VIPGuest.Command>>createTestProbe();
    var finder = testKit.spawn(GuestFinder.create(), "finder");
    finder.tell(new GuestFinder.Find("Mr.Wick", probe.ref()));
    probe.expectMessageClass(ActorRef.class);
  }

  @Test
  void ActorSubscribedToServiceKeyShouldBeNotifiedOnlyByAliveActors() {
    var wick = testKit.spawn(VIPGuest.create(), "Mr.Wick");
    wick.tell(VIPGuest.EnterHotel.INSTANCE);
    var guest = testKit.spawn(VIPGuest.create(), "Mrs.X");
    testKit.stop(wick);
    Supplier<Void> guestEnterHotel =
        () -> {
          guest.tell(VIPGuest.EnterHotel.INSTANCE);
          return null;
        };
    assertThrows(
        AssertionError.class,
        () -> LoggingTestKit.info("Mr.Wick is in").expect(testKit.system(), guestEnterHotel));
  }
}

class VIPGuest {
  public static Behavior<Command> create() {
    return Behaviors.receive(
        (context, message) -> {
          if (message instanceof EnterHotel) {
            context
                .getSystem()
                .receptionist()
                .tell(Receptionist.register(HotelConcierge.GoldenKey, context.getSelf()));
            return Behaviors.same();
          } else if (message instanceof LeaveHotel) {
            context.getLog().info("VIP guest left the hotel");
            return Behaviors.same();
          } else {
            return Behaviors.unhandled();
          }
        });
  }

  public enum EnterHotel implements Command {
    INSTANCE
  }

  public enum LeaveHotel implements Command {
    INSTANCE
  }

  public sealed interface Command {}
}

class HotelConcierge {
  public static ServiceKey<VIPGuest.Command> GoldenKey =
      ServiceKey.create(VIPGuest.Command.class, "concierge-key");

  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          var listingNotificationAdapter =
              context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

          context
              .getSystem()
              .receptionist()
              .tell(Receptionist.subscribe(GoldenKey, listingNotificationAdapter));

          return Behaviors.receiveMessage(
              message -> {
                if (message instanceof ListingResponse listingResponse) {
                  Set<ActorRef<VIPGuest.Command>> guests =
                      listingResponse.listing.getServiceInstances(GoldenKey);
                  guests.forEach(actor -> context.getLog().info("{} is in", actor.path().name()));
                  return Behaviors.same();
                } else {
                  return Behaviors.unhandled();
                }
              });
        });
  }

  public sealed interface Command {}

  private record ListingResponse(Receptionist.Listing listing) implements Command {}
}

class GuestSearch {
  public static Behavior<Command> create(
      String actorName, ActorRef<ActorRef<VIPGuest.Command>> replyTo) {
    return Behaviors.setup(
        context -> {
          var listingNotificationAdapter =
              context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);
          return Behaviors.receiveMessage(
              message -> {
                if (message instanceof Find) {
                  context
                      .getSystem()
                      .receptionist()
                      .tell(
                          Receptionist.find(HotelConcierge.GoldenKey, listingNotificationAdapter));
                  return Behaviors.same();
                } else if (message instanceof ListingResponse listingResponse) {
                  Set<ActorRef<VIPGuest.Command>> guests =
                      listingResponse.listing.getServiceInstances(HotelConcierge.GoldenKey);
                  guests.stream()
                      .filter(actor -> actor.path().name().contains(actorName))
                      .forEach(replyTo::tell);
                  return Behaviors.same();
                } else {
                  return Behaviors.unhandled();
                }
              });
        });
  }

  public enum Find implements Command {
    INSTANCE
  }

  public sealed interface Command {}

  private record ListingResponse(Receptionist.Listing listing) implements Command {}
}

class GuestFinder {
  public static Behavior<Command> create() {
    return Behaviors.setup(
        context -> {
          Duration timeout = Duration.ofSeconds(3);
          return Behaviors.receiveMessage(
              message -> {
                if (message instanceof Find find) {
                  context.ask(
                      Receptionist.Listing.class,
                      context.getSystem().receptionist(),
                      timeout,
                      replyTo -> Receptionist.find(HotelConcierge.GoldenKey, replyTo),
                      (response, throwable) -> {
                        if (throwable == null) {
                          Set<ActorRef<VIPGuest.Command>> guests =
                              response.getServiceInstances(HotelConcierge.GoldenKey);
                          guests.stream()
                              .filter(actor -> actor.path().name().contains(find.actorName()))
                              .forEach(find.replyTo()::tell);
                        } else {
                          context.getLog().error(throwable.getMessage());
                        }
                        return Void.INSTANCE;
                      });
                  return Behaviors.same();
                } else if (message instanceof Void) {
                  return Behaviors.empty();
                } else {
                  return Behaviors.unhandled();
                }
              });
        });
  }

  public enum Void implements Command {
    INSTANCE
  }

  public sealed interface Command {}

  public record Find(String actorName, ActorRef<ActorRef<VIPGuest.Command>> replyTo)
      implements Command {}
}
