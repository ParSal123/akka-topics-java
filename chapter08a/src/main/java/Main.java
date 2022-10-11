import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;

public class Main {
  public static void main(String[] args) {
    var guardian = ActorSystem.create(Behaviors.empty(), "words");
    System.out.println(guardian.settings().config().getString("akka.actor.provider"));
  }
}
