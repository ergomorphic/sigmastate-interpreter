package sigmastate.serialization.transformers

import sigmastate.Values.Value
import sigmastate.lang.Terms._
import sigmastate.serialization.OpCodes.OpCode
import sigmastate.serialization.ValueSerializer
import sigmastate.utils.{ByteReader, ByteWriter}
import sigmastate.utxo.Transformer
import sigmastate.{SBoolean, SCollection}
import sigmastate.utils.Extensions._

case class LogicalTransformerSerializer[I <: SCollection[SBoolean.type], O <: SBoolean.type]
(code: OpCode,
 cons: Value[SCollection[SBoolean.type]] => Transformer[I, O])
  extends ValueSerializer[Transformer[I, O]] {

  override val opCode: OpCode = code

  override def serializeBody(obj: Transformer[I, O], w: ByteWriter): Unit =
    w.putValue(obj.input)

  override def parseBody(r: ByteReader): Transformer[I, O] =
    cons(r.getValue().asCollection[SBoolean.type])
}