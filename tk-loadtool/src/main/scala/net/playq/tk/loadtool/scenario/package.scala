package net.playq.tk.loadtool

package object scenario {
  type ScenarioGen[T] = gen.ScenarioGen[T]
  val ScenarioGen: gen.ScenarioGen.type = gen.ScenarioGen
}
