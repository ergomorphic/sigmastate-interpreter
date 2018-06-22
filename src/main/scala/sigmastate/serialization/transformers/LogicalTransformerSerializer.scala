package sigmastate.serialization.transformers

import sigmastate.Values.Value
import sigmastate.serialization.OpCodes.OpCode
import sigmastate.serialization.Serializer.{Consumed, Position}
import sigmastate.serialization.{Serializer, ValueSerializer}
import sigmastate.utxo.Transformer
import sigmastate.{SBoolean, SCollection}
import sigmastate.lang.Terms._

case class LogicalTransformerSerializer[I <: SCollection[SBoolean.type], O <: SBoolean.type]
(code: OpCode,
 cons: Value[SCollection[SBoolean.type]] => Transformer[I, O])
  extends ValueSerializer[Transformer[I, O]] {

  override val opCode: OpCode = code

  override def parseBody(bytes: Array[Byte], pos: Position): (Transformer[I, O], Consumed) = {
    val (input, c1) = ValueSerializer.deserialize(bytes, pos)
    val i = input.asCollection[SBoolean.type]
    cons(i) -> c1
  }

  override def serializeBody(obj: Transformer[I, O]): Array[Byte] = {
    val w = Serializer.startWriter()
    w.putBytes(ValueSerializer.serialize(obj.input))
    w.toBytes
  }
}
