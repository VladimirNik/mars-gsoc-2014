/* NSC -- new Scala compiler
 * Copyright 2005-2013 LAMP/EPFL
 * @author  Martin Odersky
 */

package scala.reflect.internal
package tools.nsc

import scala.reflect.internal.util.{ SourceFile, NoSourceFile, FreshNameCreator }
import scala.collection.mutable
import scala.collection.mutable.{ LinkedHashSet, ListBuffer }
import scala.reflect.internal.tools.nsc.reporters.Reporter

trait CompilationUnits { global: Typechecker =>

  /** An object representing a missing compilation unit.
   */
  lazy val NoCompilationUnit = new CompilationUnit(NoSourceFile) {
    override lazy val isJava = false
    override def exists = false
    override def toString() = "NoCompilationUnit"
  }

  /** One unit of compilation that has been submitted to the compiler.
    * It typically corresponds to a single file of source code.  It includes
    * error-reporting hooks.  */
  class CompilationUnit(val source: SourceFile) extends CompilationUnitContextApi { self =>

    /** the fresh name creator */
    implicit val fresh: FreshNameCreator     = new FreshNameCreator
    def freshTermName(prefix: String = "x$") = global.freshTermName(prefix)
    def freshTypeName(prefix: String)        = global.freshTypeName(prefix)

    /** the content of the compilation unit in tree form */
    var body: Tree = EmptyTree

    /** The position of the first xml literal encountered while parsing this compilation unit.
     * NoPosition if there were none. Write-once.
     */
    private[this] var _firstXmlPos: Position = NoPosition

    //TODO-REFLECT change access modifiers to original:
    //protected[nsc] def encounteredXml(pos: Position) = _firstXmlPos = pos
    /** Record that we encountered XML. Should only be called once. */
    def encounteredXml(pos: Position) = _firstXmlPos = pos

    /** Does this unit contain XML? */
    def hasXml = _firstXmlPos ne NoPosition

    /** Position of first XML literal in this unit. */
    def firstXmlPos = _firstXmlPos

    def exists = source != NoSourceFile && source != null

    /** Note: depends now contains toplevel classes.
     *  To get their sourcefiles, you need to dereference with .sourcefile
     */
    private[this] val _depends = mutable.HashSet[Symbol]()
    // SBT compatibility (SI-6875)
    //
    // imagine we have a file named A.scala, which defines a trait named Foo and a module named Main
    // Main contains a call to a macro, which calls compileLate to define a mock for Foo
    // compileLate creates a virtual file Virt35af32.scala, which contains a class named FooMock extending Foo,
    // and macro expansion instantiates FooMock. the stage is now set. let's see what happens next.
    //
    // without this workaround in scalac or without being patched itself, sbt will think that
    // * Virt35af32 depends on A (because it extends Foo from A)
    // * A depends on Virt35af32 (because it contains a macro expansion referring to FooMock from Virt35af32)
    //
    // after compiling A.scala, SBT will notice that it has a new source file named Virt35af32.
    // it will also think that this file hasn't yet been compiled and since A depends on it
    // it will think that A needs to be recompiled.
    //
    // recompilation will lead to another macro expansion. that another macro expansion might choose to create a fresh mock,
    // producing another virtual file, say, Virtee509a, which will again trick SBT into thinking that A needs a recompile,
    // which will lead to another macro expansion, which will produce another virtual file and so on
    def depends = if (exists && !source.file.isVirtual) _depends else mutable.HashSet[Symbol]()

    /** so we can relink
     */
    private[this] val _defined = mutable.HashSet[Symbol]()
    def defined = if (exists && !source.file.isVirtual) _defined else mutable.HashSet[Symbol]()

    /** Synthetic definitions generated by namer, eliminated by typer.
     */
    object synthetics {
      private val map = mutable.HashMap[Symbol, Tree]()
      def update(sym: Symbol, tree: Tree) {
        debuglog(s"adding synthetic ($sym, $tree) to $self")
        map.update(sym, tree)
      }
      def -=(sym: Symbol) {
        debuglog(s"removing synthetic $sym from $self")
        map -= sym
      }
      def get(sym: Symbol): Option[Tree] = debuglogResultIf[Option[Tree]](s"found synthetic for $sym in $self", _.isDefined) {
        map get sym
      }
      def keys: Iterable[Symbol] = map.keys
      def clear(): Unit = map.clear()
      override def toString = map.toString
    }

    // namer calls typer.computeType(rhs) on DefDef / ValDef when tpt is empty. the result
    // is cached here and re-used in typedDefDef / typedValDef
    // Also used to cache imports type-checked by namer.
    val transformed = new mutable.AnyRefMap[Tree, Tree]

    /** things to check at end of compilation unit */
    val toCheck = new ListBuffer[() => Unit]

    /** The features that were already checked for this unit */
    var checkedFeatures = Set[Symbol]()

    def position(pos: Int) = source.position(pos)

    /** The position of a targeted type check
     *  If this is different from NoPosition, the type checking
     *  will stop once a tree that contains this position range
     *  is fully attributed.
     */
    def targetPos: Position = NoPosition

    /** The icode representation of classes in this compilation unit.
     *  It is empty up to phase 'icode'.
     */

    //TODO-REFLECT: val icode: LinkedHashSet[icodes.IClass] = new LinkedHashSet
    type Icode
    /** The icode representation of classes in this compilation unit.
     *  It is empty up to phase 'icode'.
     */
    def icode: LinkedHashSet[Icode] = new LinkedHashSet

    def reporter = global.reporter

    def echo(pos: Position, msg: String) =
      reporter.echo(pos, msg)

    def error(pos: Position, msg: String) =
      reporter.error(pos, msg)

    def warning(pos: Position, msg: String) =
      reporter.warning(pos, msg)

    def deprecationWarning(pos: Position, msg: String) =
      currentRun.deprecationWarnings0.warn(pos, msg)

    def uncheckedWarning(pos: Position, msg: String) =
      currentRun.uncheckedWarnings0.warn(pos, msg)

    def inlinerWarning(pos: Position, msg: String) =
      currentRun.inlinerWarnings.warn(pos, msg)

    def incompleteInputError(pos: Position, msg:String) =
      reporter.incompleteInputError(pos, msg)

    def comment(pos: Position, msg: String) =
      reporter.comment(pos, msg)

    /** Is this about a .java source file? */
    lazy val isJava = source.file.name.endsWith(".java")

    override def toString() = source.toString()
  }
}