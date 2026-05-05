package net.playq.tk.util.magnoliacompat

object MagnoliaCompat {
  type ReadOnlyCaseClass[Typeclass[_], Type] = magnolia1.ReadOnlyCaseClass[Typeclass, Type]
  type CaseClass[Typeclass[_], Type]         = magnolia1.CaseClass[Typeclass, Type]
  type SealedTrait[Typeclass[_], Type]       = magnolia1.SealedTrait[Typeclass, Type]
}
