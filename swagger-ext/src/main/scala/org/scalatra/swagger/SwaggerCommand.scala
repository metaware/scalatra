package org.scalatra
package swagger

import org.scalatra.commands._
import org.scalatra.util.RicherString._
import scalaz._
import Scalaz._
import swagger.SwaggerSupportSyntax.SwaggerOperationBuilder
import swagger.SwaggerCommandSupport.CommandOperationBuilder

object SwaggerCommandSupport {
  
  private[this] val paramtypeMapping = 
                        Map(
                            ValueSource.Body -> ParamType.Body, 
                            ValueSource.Header -> ParamType.Header, 
                            ValueSource.Query -> ParamType.Query, 
                            ValueSource.Path -> ParamType.Path)
                            
  def parametersFromCommand[T <: Command](obj: T)(implicit mf: Manifest[T]): (List[Parameter], Option[Model]) = {
    addModelFromCommand(obj, createParameterList(obj))
  }

  private[this] def createParameterList[T <: Command](obj: T)(implicit mf: Manifest[T]): List[Parameter] = {
    mf.erasure.getMethods().foldLeft(List.empty[Parameter]) { (lst, fld) =>
      if (fld.getReturnType().isAssignableFrom(classOf[Field[_]]) && fld.getParameterTypes().isEmpty) {
        val f = fld.invoke(obj).asInstanceOf[Field[_]]
        // remove if statement below to include header params in description again
        if (f.valueSource == ValueSource.Header) lst else {
          Parameter(
              f.displayName | f.name,
              f.description,
              DataType(f.binding.valueManifest),
              f.notes.blankOption,
              paramtypeMapping(f.valueSource),
              if (f.isRequired) None else f.defaultValue.toString.blankOption,
              if (f.allowableValues.nonEmpty) AllowableValues(f.allowableValues) else AllowableValues.AnyValue,
              required = f.isRequired) :: lst
        }
      } else lst
    }
  }

  private[this] def addModelFromCommand[T <: Command](obj: T, pars: List[Parameter])(implicit mf: Manifest[T]) = {
    val (fields, parameters) = pars.partition(_.paramType == ParamType.Body)
    if (fields.nonEmpty) {
      val model = modelFromCommand(obj, fields)
      val bodyParam = 
          Parameter(
              "body", 
              model.description, 
              DataType(model.id), 
              None, 
              ParamType.Body, 
              None) 
      (bodyParam :: parameters, Some(model)) 
    } else (parameters, None)
  }

  private[this] def modelFromCommand[T <: Command](cmd: T, fields: List[Parameter]) = {
    val modelFields = fields.map(f => f.name -> ModelField(f.name, f.description, f.dataType, f.defaultValue, required = f.required))
    Model(cmd.commandName, cmd.commandDescription, Map(modelFields:_*))
  }

  class CommandOperationBuilder[B <: SwaggerOperationBuilder[_]](registerModel: Model => Unit, underlying: B) {
    def parametersFromCommand[C <: Command : Manifest]: B =
      parametersFromCommand(manifest[C].erasure.newInstance().asInstanceOf[C])

    def parametersFromCommand[C <: Command : Manifest](cmd: => C): B = {
      SwaggerCommandSupport.parametersFromCommand(cmd) match {
        case (parameters, None) =>
        case (parameters, Some(model)) =>
          registerModel(model)
          underlying.parameters(parameters:_*)
      }
      underlying
    }
  }
}
trait SwaggerCommandSupport { this: ScalatraBase with SwaggerSupportBase with SwaggerSupportSyntax with CommandSupport =>

  @deprecated("Use the `apiOperation.parameters` and `operation` methods to build swagger descriptions of endpoints", "2.2")
  protected def parameters[T <: CommandType : Manifest] =
    swaggerMeta(Symbols.Parameters, parametersFromCommand[T])

  @deprecated("Use the `apiOperation.parameters` and `operation` methods to build swagger descriptions of endpoints", "2.2")
  protected def parameters[T <: CommandType : Manifest](cmd: => T) =
    swaggerMeta(Symbols.Parameters, parametersFromCommand(cmd))

  protected implicit def operationBuilder2commandOpBuilder[B <: SwaggerOperationBuilder[_]](underlying: B) =
    new CommandOperationBuilder(registerModel(_), underlying)
  
  
  private[this] def parametersFromCommand[T <: CommandType](implicit mf: Manifest[T]): List[Parameter] = {
    parametersFromCommand(mf.erasure.newInstance().asInstanceOf[T])
  }

  private[this] def parametersFromCommand[T <: CommandType](cmd: => T)(implicit mf: Manifest[T]): List[Parameter] = {
      SwaggerCommandSupport.parametersFromCommand(cmd) match {
        case (parameters, None) => parameters
        case (parameters, Some(model)) =>
          _models += model
          parameters
      }
    }
}
