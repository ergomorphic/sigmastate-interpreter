package sigmastate.lang

import org.bitbucket.inkytonik.kiama.attribution.Attribution
import sigmastate._
import sigmastate.Values._
import sigmastate.lang.Terms._
import sigmastate.utxo.{Inputs, ByIndex}

/**
  * Analyses for typed lambda calculus expressions.  A simple free variable
  * analysis plus name and type analysis.  There are two versions of the
  * latter here: one (tipe) that constructs an explicit environment separate
  * from the AST, and one (tipe2) that represents names by references to the
  * nodes of their binding lambda expressions.
  */
class SigmaTyper(globalEnv: Map[String, Any], tree : SigmaTree) extends Attribution {
  import SigmaTyper._
  import PrettyPrinter.formattedLayout
  import org.bitbucket.inkytonik.kiama.util.Messaging.{check, collectMessages, noMessages, message, Messages}

  /** The semantic error messages for the tree. */
  lazy val errors : Messages =
    collectMessages(tree) {
      case e : SValue =>
//        checkType(e, tipe) ++
            check(e) {
              case e @ Ident(x, _) =>
                message(e, s"unknown name '$x'", tipe(e) == NoType)
              case e: SValue =>
                message(e, s"Expression ${e} doesn't have type: context ${tree.parent(e)}", tipe(e) == NoType)
            }
    }

  /**
    * The variables that are free in the given expression.
    */
  val fv : SValue => Set[Idn] =
    attr {
      case IntConstant(_)            => Set()
      case Ident(v, _)            => Set(v)
      case Lambda(args, _, Some(e))      => fv(e) -- args.map(_._1).toSet
      case Apply(e1, args)       => args.foldLeft(fv(e1))((acc, e) => acc ++ fv(e))
      case Let(i, t, e) => fv(e)
      case Block(bs, e) => {
        val fbv = bs.map(_.body).map(fv).flatten.toSet
        val bvars = bs.map(_.name).toSet
        fbv ++ (fv(e) -- bvars)
      }
    }

  /**
    * The environment of an expression is the list of variable names that
    * are visible in that expression and their types.
    */
  val env : SValue => List[(Idn, SType)] =
    attr {

      // Inside a lambda expression the bound variable is now visible
      // in addition to everything that is visible from above. Note
      // that an inner declaration of a var hides an outer declaration
      // of the same var since we add inner bindings at the beginning
      // of the env and we search the env list below in tipe from
      // beginning to end
      case e @ tree.parent(p @ Lambda(args, t, Some(body))) if e eq body =>
        (args ++ env(p)).toList

      // Inside the result expression of a block all bindings are visible
      case e @ tree.parent(p @ Block(bs, res)) if e eq res =>
        (bs.map(l => (l.name, l.givenType.?:(tipe(l.body)))) ++ env(p)).toList

      // Inside any binding of a block all the previous names are visible
      case e @ tree.parent(p @ Block(bs, res)) if bs.exists(_ eq e) =>
        val iLet = bs.indexWhere(_ eq e)
        val boundNames = bs.take(iLet).map(l => (l.name, l.givenType.?:(tipe(l.body))))
        (boundNames ++ env(p)).toList

      // Other expressions do not bind new identifiers so they just
      // get their environment from their parent
      case tree.parent(p : SValue) =>
        env(p)

      // global level contains all predefined names
      case _ =>
        val predef = SigmaPredef.predefinedEnv.mapValues(_.tpe).toList
        predef ++ globalEnv.mapValues(SType.typeOfData).toList
    }

  /**
    * Check that the type of `e` is its expected type or unknown. If not,
    * return an error. `tipe` is used to obtain the type.
    */
  def checkType(e : SValue, tipe : SValue => SType) : Messages = {
    val expectedType = exptipe(e)
    message(e, s"expected ${formattedLayout(expectedType)}, found ${formattedLayout(tipe(e))}",
      tipe(e) != NoType && expectedType != NoType && tipe(e) != expectedType)
  }

  /**
    * The type of an expression.  Checks constituent names and types.  Uses
    * the env attribute to get the bound variables and their types.
    */
  val tipe : SValue => SType =
    attr {
      // this case should be before general EvaluatedValue
      case c @ ConcreteCollection(items) =>  {
        val types = items.map(tipe).distinct
        val eItem =
          if (types.isEmpty) NoType
          else
          if (types.size == 1) types(0)
          else
            error(s"All element of array $c should have the same type but found $types")
        SCollection(eItem)
      }

      // this case should be before general EvaluatedValue
      case Tuple(items) =>
        STuple(items.map(tipe))

      case v: EvaluatedValue[_] => v.tpe
      case v: NotReadyValueInt => v.tpe
      case Inputs => Inputs.tpe

      // An operation must be applied to two arguments of the same type
      case op @ GT(e1, e2) => binOpTipe(op, e1, e2)(SInt, SBoolean)
      case op @ LT(e1, e2) => binOpTipe(op, e1, e2)(SInt, SBoolean)
      case op @ GE(e1, e2) => binOpTipe(op, e1, e2)(SInt, SBoolean)
      case op @ LE(e1, e2) => binOpTipe(op, e1, e2)(SInt, SBoolean)
      case op @ EQ(e1, e2) => binOpTipe(op, e1, e2)(tipe(e1), SBoolean)
      case op @ NEQ(e1, e2) => binOpTipe(op, e1, e2)(tipe(e1), SBoolean)

      case op @ AND(xs) =>
        val xsT = checkTyped(op, tipe(xs), SCollection(SBoolean))
        xsT.elemType
      case op @ OR(xs) =>
        val xsT = checkTyped(op, tipe(xs), SCollection(SBoolean))
        xsT.elemType

      case ite @ If(c, t, e) =>
        val tCond = tipe(c)
        if (tCond != SBoolean) error(s"Invalid type of condition in $ite: expected Boolean; actual: $tCond")
        val tThen = tipe(t)
        val tElse = tipe(e)
        if (tThen != tElse) error(s"Invalid type of condition $ite: both branches should have the same type but was $tThen and $tElse")
        tThen
      // An identifier is looked up in the environement of the current
      // expression.  If we find it, then we use the type that we find.
      // Otherwise it's an error.
      case e @ Ident(x,_) =>
        env(e).collectFirst {
          case (y, t) if x == y => t
        }.getOrElse {
          NoType
        }

      // A lambda expression is a function from the type of its argument
      // to the type of the body expression
      case lam @ Lambda(args, t, body) =>
        for ((name, t) <- args)
          if (t == NoType)
            error(s"Invalid function $lam: undefined type of argument $name")
        val argTypes = args.map(_._2)
        if (t == NoType) {
          val tRes = body.fold(NoType: SType)(tipe)
          if (tRes == NoType)
            error(s"Invalid function $lam: undefined type of result")
          SFunc(argTypes, tRes)
        }
        else
          SFunc(argTypes, t)

      // For an application we first determine the type of the expression
      // being applied.  If it's a function then the application has type
      // of that function's return type.
      case app @ Apply(f, args) =>
        tipe(f) match {
          case SFunc(argTypes, tRes) =>
            val actualTypes = args.map(tipe)
            if (actualTypes == argTypes)
              tRes
            else
              error(s"Invalid argument type of application $app: expected $argTypes; actual: $actualTypes")
          case tCol: SCollection[_] =>
            args match {
              case Seq(IntConstant(i)) =>
                tCol.elemType
              case _ =>
                error(s"Invalid argument of array application $app: expected integer constant; actual: $args")
            }
          case t =>
            error(s"Invalid function/array application $app: function/array type is expected but was $t")
        }

      case ByIndex(col, i) =>
        val tItem = col.tpe.elemType
        if (tItem == NoType) error(s"Invalid type in $col: undefined element type")
        tItem

      // A block returns the type of the result expression
      case Block(bs, e) =>
        tipe(e)

      // Let can be thought as an expression with the side effect of introducing a new variable
      case Let(_, _, body) => tipe(body)

      case Select(obj, field) =>
        tipe(obj) match {
          case s: SProduct =>
            val iField = s.fieldIndex(field)
            if (iField != -1) {
              s.fields(iField)._2
            }
            else
              error(s"Cannot find field '$field' in product type with fields ${s.fields}")
          case t =>
            error(s"Cannot get field '$field' of non-product type $t")
        }

      case e => error(s"Don't know how to compute type for $e")
    }

  def binOpTipe(op: SValue, e1: SValue, e2: SValue)(arg: SType, res: SType): SType =
    if ((tipe(e1) == arg) && (tipe(e2) == arg))
      res
    else
      error(s"Invalid binary operation $op: expected argument types ($arg, $arg); actual: (${tipe(e1)}, ${tipe(e2)})")

  def checkTyped[T <: SType](op: SValue, t: SType, expected: T): T =
    if (t == expected)
      t.asInstanceOf[T]
    else
      error(s"Invalid argument type $t of $op")

  /** The expected type of an expression. */
  val exptipe : SValue => SType =
    attr {

      // An applied expression is allowed to be anything. We check
      // elsewhere that it's a function.
      case e @ tree.parent(Apply(e1, _)) if e eq e1 => SAny

      // An argument is expected to be of the function's input type
      case e @ tree.parent(Apply(e1, e2)) if e eq e2 => SAny
//        tipe(e1) match {
//          case FunType(t1, _) =>
//            t1
//          case _ =>
//            NoType()
//        }

      // The type of let body must match the declared type
      case e @ tree.parent(p @ Let(_, t, body)) if (e eq body) && t != NoType => t

      // The operands of an operation should be integers
      case tree.parent(Plus(_, _)) => SInt
      case tree.parent(Minus(_, _)) => SInt

      // Other expressions are allowed to be anything
      case _ => SAny
    }

}

class TyperException(msg: String) extends Exception(msg)

object SigmaTyper {
  def error(msg: String) = throw new TyperException(msg)
}