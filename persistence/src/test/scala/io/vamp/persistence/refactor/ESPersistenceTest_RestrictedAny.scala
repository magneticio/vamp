package io.vamp.persistence.refactor

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.vamp.common._
import io.vamp.persistence.refactor.serialization.{SerializationSpecifier, VampJsonFormats}
import org.scalatest.{BeforeAndAfterEach, Matchers, fixture}

/**
 * Created by mihai on 11/10/17.
 */
class ESPersistenceTest_RestrictedAny extends fixture.FlatSpec with Matchers with UseElasticSearchForTesting with BeforeAndAfterEach with VampJsonFormats {

  behavior of "EsDao"
  it should "Correctly Persist RestrictedAny objects" in { implicit namespace: Namespace ⇒
    case class SimpleCatalog (name: String, compilationYear: Int, attributes: RootAnyMap, entries: RootAnyMap)

    val simpleCatalogDecoder: Decoder[SimpleCatalog] = deriveDecoder[SimpleCatalog]
    val simpleCatalogEncoder: Encoder[SimpleCatalog] = deriveEncoder[SimpleCatalog]
    implicit val simpleCatalogSerilizationSpecifier = SerializationSpecifier[SimpleCatalog](encoder = simpleCatalogEncoder,
      decoder = simpleCatalogDecoder, typeName = "simpleCatalog", idExtractor = (e ⇒ Id[SimpleCatalog](e.name)))

    val obj1 = SimpleCatalog(name = "catalog1", compilationYear = 2017,
      attributes = RootAnyMap(Map[String, RestrictedAny](
        "type" -> RestrictedString("bookCatalog"),
        "numberOfEntries" -> RestrictedInt(3),
        "otherListOfBooks" -> RestrictedList(List(
          RestrictedString("A Brief History Of Time"),
          RestrictedMap(Map(
            "Author" -> RestrictedString("Stephen Hawking"),
            "publishingYear" -> RestrictedInt(1988)
          )),
          RestrictedBoolean(true)
        )),
        "completePercentage" -> RestrictedDouble(66.6),
        "isComplete" -> RestrictedBoolean(false),
        "metadata" -> RestrictedMap(Map[String, RestrictedAny](
          "Dostoevsky" -> RestrictedString("wrote one book on the list"),
          "compilationYear" -> RestrictedInt(2017)
        ))
      )),
      entries = RootAnyMap(Map[String, RestrictedAny](
        "totalNumberOfPages" -> RestrictedInt(1072 + 341),
        "enteredSoFar" -> RestrictedInt(2),
        "totalWeightInPounds" -> RestrictedDouble(1.1 + 0.53),
        "taleOfTwoCities" -> RestrictedMap(Map[String, RestrictedAny](
          "numberOfPages" -> RestrictedInt(341),
          "bestFirstLineEver" -> RestrictedString("It was the \"best\" of \"\"times\"\", It was the worst of times")
        ))
      )))

    val gateway1Id = simpleAwait(VampPersistence().create(obj1))
    assert(simpleAwait(VampPersistence().read(gateway1Id)) == obj1)
  }

}
