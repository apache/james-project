import org.apache.james.blob.api.{BlobId, TestBlobId}
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite

case class Generation(id: Long)
case class Iteration(id: Long)
case class ExternalID(id: String) // TODO

sealed trait Event
case class Reference(externalId: ExternalID, blobId: BlobId, generation: Generation) extends Event
case class Deletion(generation: Generation, reference: Reference) extends Event

case class Report(iteration: Iteration,
                  blobsToDelete: Set[(Generation, BlobId)]
                 )

object Generators {

  val smallInteger = Gen.choose(0L,100L)
  var current = 0;
  val generationsGen: Gen[LazyList[Generation]] = Gen.infiniteLazyList(Gen.frequency((90, Gen.const(0)), (9, Gen.const(1)), (1, Gen.const(2))))
    .map(list => list.scanLeft(0)(_ + _))
    .map(list => list.map(_.toLong).map(Generation.apply))

  val iterationGen = smallInteger.map(Iteration.apply)

  val blobIdFactory = new TestBlobId.Factory

  def blobIdGen(generation: Generation) : Gen[BlobId] = Gen.uuid.map(uuid =>
    blobIdFactory.from(s"${generation}_$uuid"))

  val externalIDGen = Gen.uuid.map(uuid => ExternalID(uuid.toString))

  def referenceGen(generation: Generation): Gen[Reference] = for {
    blobId <- blobIdGen(generation)
    externalId <- externalIDGen
  } yield Reference(externalId, blobId, generation)

  def existingReferences : Seq[Event] => Set[Reference] = _
    .foldLeft((Set[Reference](), Set[Reference]()))((acc, event) => event match {
      case deletion: Deletion => (acc._1 ++ Set(deletion.reference), acc._2)
      case reference: Reference => if (acc._1.contains(reference)) {
        acc
      } else {
        (acc._1, acc._2 ++ Set(reference))
      }
    })._2

  def deletionGen(previousEvents : Seq[Event], generation: Generation): Gen[Option[Deletion]] = {
    val persistingReferences = existingReferences(previousEvents)
    if (persistingReferences.isEmpty) {
      Gen.const(None)
    } else {
      Gen.oneOf(persistingReferences)
        .map(reference => Deletion(generation, reference))
        .map(Some(_))
    }
  }

  def duplicateReferenceGen(generation: Generation, reference: Reference): Gen[Reference] = {
    if (reference.generation == generation) {
      externalIDGen.map(id => reference.copy(externalId = id))
    } else {
      referenceGen(generation)
    }
  }

  def eventGen(previousEvents: Seq[Event], generation: Generation): Gen[Event] = for {
    greenAddEvent <- referenceGen(generation)
    addEvents = previousEvents.flatMap {
      case x: Reference => Some(x)
      case _ => None
    }
    randomAddEvent <- Gen.oneOf(addEvents)
    duplicateAddEvent <- duplicateReferenceGen(generation, randomAddEvent)
    deleteEvent <- deletionGen(previousEvents, generation)
    event <- Gen.oneOf(Seq(greenAddEvent, duplicateAddEvent) ++ deleteEvent)
  } yield event

  def eventsGen() : Gen[Seq[Event]] = for {
    nbEvents <- Gen.choose(0, 100)
    generations <- generationsGen.map(_.take(nbEvents))
    startEvent <- referenceGen(Generation.apply(0))
    events <- foldM(generations, (Seq(startEvent): Seq[Event]))((previousEvents, generation) => eventGen(previousEvents, generation).map(_ +: previousEvents))
  } yield events.reverse

  def foldM[A, B](fa: LazyList[A], z: B)(f: (B, A) => Gen[B]): Gen[B] = {
    def step(in: (LazyList[A], B)): Gen[Either[(LazyList[A], B), B]] = {
      val (s, b) = in
      if (s.isEmpty)
        Gen.const(Right(b))
      else {
        f (b, s.head).map { bnext =>
          Left((s.tail, bnext))
        }
      }
    }

    Gen.tailRecM((fa, z))(step)
  }
}

class GCPropertiesTest extends AnyFunSuite {
  test("print sample") {
    Generators.eventsGen().sample.foreach(_.foreach(println))
  }
}
