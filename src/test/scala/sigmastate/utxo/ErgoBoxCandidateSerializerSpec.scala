package sigmastate.utxo

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import sigmastate.serialization.generators.ValueGenerators

class ErgoBoxCandidateSerializerSpec extends PropSpec
  with GeneratorDrivenPropertyChecks
  with SerializationRoundTripSpec
  with ValueGenerators {

  private val ergoBoxCandidateSerializer = ErgoBoxCandidate.serializer

  property("ErgoBoxCandidate: Serializer round trip") {
    forAll { b: ErgoBoxCandidate =>
      ergoBoxCandidateSerializer.parseBytes(ergoBoxCandidateSerializer.toBytes(b)).get shouldEqual b
    }
  }

  property("ErgoBoxCandidate: start pos and consumed bytes") {
    forAll { b: ErgoBoxCandidate =>
      val randomBytesCount = Gen.chooseNum(1, 20).sample.get
      val randomBytes = Gen.listOfN(randomBytesCount, arbByte.arbitrary).sample.get.toArray
      val bytes = ergoBoxCandidateSerializer.toBytes(b)
      ergoBoxCandidateSerializer.parseBody(randomBytes ++ bytes, randomBytesCount) shouldEqual (b, bytes.length)
    }
  }
}
