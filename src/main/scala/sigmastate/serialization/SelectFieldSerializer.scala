package sigmastate.serialization

import sigmastate.Values.Value
import sigmastate.lang.Terms._
import sigmastate.serialization.OpCodes._
import sigmastate.utils.Extensions._
import sigmastate.utils.{ByteReader, ByteWriter}
import sigmastate.utxo.SelectField
import sigmastate.{STuple, SType}

case class SelectFieldSerializer(cons: (Value[STuple], Byte) => Value[SType]) extends ValueSerializer[SelectField] {

  override val opCode: Byte = SelectFieldCode

  override def serializeBody(obj: SelectField, w: ByteWriter): Unit =
    w.putValue(obj.input)
      .put(obj.fieldIndex)

  override def parseBody(r: ByteReader): Value[SType] = {
    val tuple = r.getValue().asValue[STuple]
    val fieldIndex = r.getByte()
    cons(tuple, fieldIndex)
  }

}
