package sigmastate.serialization.generators

import org.scalacheck.{Arbitrary, Gen}
import sigmastate.Values.{TrueLeaf, FalseLeaf}
import sigmastate.{SInt, IsMember, If}

trait RelationGenerators { this: ValueGenerators with ConcreteCollectionGenerators =>

  implicit val arbIsMember: Arbitrary[IsMember] = Arbitrary(isMemberGen)
  implicit val arbIf: Arbitrary[If[SInt.type]] = Arbitrary(ifGen)

  val isMemberGen: Gen[IsMember] = for {
    t <- arbTaggedAvlTree.arbitrary
    b1 <- arbByteArrayConstant.arbitrary
    b2 <- arbByteArrayConstant.arbitrary
  } yield IsMember(t, b1, b2)

  val ifGen: Gen[If[SInt.type]] = for {
    c <- Gen.oneOf(TrueLeaf, FalseLeaf)
    tb <- arbIntConstants.arbitrary
    fb <- arbIntConstants.arbitrary
  } yield If(c, tb, fb)


}
