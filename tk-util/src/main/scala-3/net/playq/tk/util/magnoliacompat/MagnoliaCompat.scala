package net.playq.tk.util.magnoliacompat

import magnolia1.SealedTrait.{Subtype, SubtypeValue}
import magnolia1.{CaseClass, TypeInfo}

object MagnoliaCompat {
  type ReadOnlyCaseClass[Typeclass[_], Type] = magnolia1.CaseClass[Typeclass, Type]
  type CaseClass[Typeclass[_], Type]         = magnolia1.CaseClass[Typeclass, Type]
  type SealedTrait[Typeclass[_], Type]       = magnolia1.SealedTrait[Typeclass, Type]

  extension [Typeclass[_], Type](
    p: CaseClass.Param[Typeclass, Type]
  ) def dereference(param: Type): p.PType = p.deref(param)

  // todo: figure out how extract type info from the parameter
  //  can't do that until upstream is fixed - https://github.com/softwaremill/magnolia/issues/582
  extension [Typeclass[_], Type](
    p: CaseClass.Param[Typeclass, Type]
  ) def typeName: TypeInfo = TypeInfo("", "`unknown type: see https://github.com/softwaremill/magnolia/issues/582`", Iterable.empty)

  extension [Typeclass[_], Type](
    ctx: ReadOnlyCaseClass[Typeclass, Type]
  ) def typeName: TypeInfo = ctx.typeInfo

  extension [Typeclass[_], Type, S](
    subtype: SubtypeValue[Typeclass, Type, S]
  ) def typeName: TypeInfo = subtype.typeInfo

  extension [Typeclass[_], Type, S](
    subtype: Subtype[Typeclass, Type, S]
  ) def typeName: TypeInfo = subtype.typeInfo

  extension [Typeclass[_], Type](
    ctx: SealedTrait[Typeclass, Type]
  ) def split[Return](value: Type)(handle: ctx.Subtype[?] => Return): Return = ctx.choose(value)(handle)

  extension [Typeclass[_], Type](
    ctx: SealedTrait[Typeclass, Type]
  ) def typeName: TypeInfo = ctx.typeInfo
}
