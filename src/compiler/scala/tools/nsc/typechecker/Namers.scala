/* NSC -- new Scala compiler
 * Copyright 2005-2013 LAMP/EPFL
 * @author  Martin Odersky
 */

package scala.tools.nsc
package typechecker

trait Namers extends scala.reflect.internal.tools.nsc.typechecker.Namers with MethodSynthesis {
  self: Analyzer =>
}
