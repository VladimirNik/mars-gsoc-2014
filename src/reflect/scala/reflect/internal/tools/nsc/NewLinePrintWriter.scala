package scala.reflect.internal.tools.nsc

import java.io.{Writer, PrintWriter}

class NewLinePrintWriter(out: Writer, autoFlush: Boolean)
extends PrintWriter(out, autoFlush) {
  def this(out: Writer) = this(out, false)
  override def println() { print("\n"); flush() }
}
