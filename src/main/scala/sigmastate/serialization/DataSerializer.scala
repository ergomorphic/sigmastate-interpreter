package sigmastate.serialization

import java.math.BigInteger
import java.nio.charset.StandardCharsets

import org.ergoplatform.ErgoBox
import sigmastate.Values.SigmaBoolean
import sigmastate.utils.{ByteReader, ByteWriter}
import sigmastate.utils.Extensions._
import sigmastate._
import sigmastate.interpreter.CryptoConstants.EcPointType

import scala.collection.mutable

/** This works in tandem with ConstantSerializer, if you change one make sure to check the other.*/
object DataSerializer {

  def serialize[T <: SType](v: T#WrappedType, tpe: T, w: ByteWriter): Unit = tpe match {
    case SUnit => // don't need to save anything
    case SBoolean => w.putBoolean(v.asInstanceOf[Boolean])
    case SByte => w.put(v.asInstanceOf[Byte])
    case SShort => w.putShort(v.asInstanceOf[Short])
    case SInt => w.putInt(v.asInstanceOf[Int])
    case SLong => w.putLong(v.asInstanceOf[Long])
    case SString =>
      val bytes = v.asInstanceOf[String].getBytes(StandardCharsets.UTF_8)
      w.putUInt(bytes.length)
      w.putBytes(bytes)
    case SBigInt =>
      val data = v.asInstanceOf[BigInteger].toByteArray
      w.putUShort(data.length)
      w.putBytes(data)
    case SGroupElement =>
      GroupElementSerializer.serializeBody(v.asInstanceOf[EcPointType], w)
    case SSigmaProp =>
      val p = v.asInstanceOf[SigmaBoolean]
      w.putValue(p)
    case SBox =>
      ErgoBox.serializer.serializeBody(v.asInstanceOf[ErgoBox], w)
    case SAvlTree =>
      AvlTreeData.serializer.serializeBody(v.asInstanceOf[AvlTreeData], w)
    case tCol: SCollectionType[a] =>
      val arr = v.asInstanceOf[tCol.WrappedType]
      w.putUShort(arr.length)
      tCol.elemType match {
        case SBoolean =>
          w.putBits(arr.asInstanceOf[Array[Boolean]])
        case SByte =>
          w.putBytes(arr.asInstanceOf[Array[Byte]])
        case _ =>
          arr.foreach(x => serialize(x, tCol.elemType, w))
      }

    case t: STuple =>
      val arr = v.asInstanceOf[t.WrappedType]
      val len = arr.length
      assert(arr.length == t.items.length, s"Type $t doesn't correspond to value $arr")
      if (len > 0xFFFF)
        sys.error(s"Length of tuple $arr exceeds ${0xFFFF} limit.")
      var i = 0
      while (i < arr.length) {
        serialize[SType](arr(i), t.items(i), w)
        i += 1
      }

    case _ => sys.error(s"Don't know how to serialize ($v, $tpe)")
  }

  def deserialize[T <: SType](tpe: T, r: ByteReader): (T#WrappedType) = (tpe match {
    case SUnit => ()
    case SBoolean => r.getUByte() != 0
    case SByte => r.getByte()
    case SShort => r.getShort()
    case SInt => r.getInt()
    case SLong => r.getLong()
    case SString =>
      val size = r.getUInt().toInt
      val bytes = r.getBytes(size)
      new String(bytes, StandardCharsets.UTF_8)
    case SBigInt =>
      val size: Short = r.getUShort().toShort
      val valueBytes = r.getBytes(size)
      new BigInteger(valueBytes)
    case SGroupElement =>
      GroupElementSerializer.parseBody(r)
    case SSigmaProp =>
      val p = r.getValue().asInstanceOf[SigmaBoolean]
      p
    case SBox =>
      ErgoBox.serializer.parseBody(r)
    case SAvlTree =>
      AvlTreeData.serializer.parseBody(r)
    case tCol: SCollectionType[a] =>
      val len = r.getUShort()
      if (tCol.elemType == SByte)
        r.getBytes(len)
      else
        deserializeArray(len, tCol.elemType, r)
    case tuple: STuple =>
      val arr =  tuple.items.map { t =>
        deserialize(t, r)
      }.toArray[Any]
      arr
    case _ => sys.error(s"Don't know how to deserialize $tpe")
  }).asInstanceOf[T#WrappedType]

  def deserializeArray[T <: SType](len: Int, tpe: T, r: ByteReader): Array[T#WrappedType] =
    tpe match {
      case SBoolean =>
        r.getBits(len).asInstanceOf[Array[T#WrappedType]]
      case _ =>
        val b = mutable.ArrayBuilder.make[T#WrappedType]()(tpe.classTag)
        for (i <- 0 until len) {
          b += deserialize(tpe, r)
        }
        b.result()
    }
}
