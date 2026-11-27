package app.komikku.desktop.extension.dex

import com.googlecode.d2j.converter.IR2JConverter
import com.googlecode.d2j.dex.ClassVisitorFactory
import com.googlecode.d2j.dex.Dex2Asm
import com.googlecode.d2j.dex.DexExceptionHandler
import com.googlecode.d2j.dex.ExDex2Asm
import com.googlecode.d2j.dex.LambadaNameSafeClassAdapter
import com.googlecode.d2j.node.DexClassNode
import com.googlecode.d2j.node.DexFileNode
import com.googlecode.d2j.node.DexMethodNode
import com.googlecode.d2j.reader.DexFileReader
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.ir.IrMethod
import com.googlecode.dex2jar.ir.expr.Exprs
import com.googlecode.dex2jar.ir.expr.InvokeExpr
import com.googlecode.dex2jar.ir.expr.Local
import com.googlecode.dex2jar.ir.expr.NewExpr
import com.googlecode.dex2jar.ir.expr.Value
import com.googlecode.dex2jar.ir.stmt.Stmt
import com.googlecode.dex2jar.ir.ts.AggTransformer
import com.googlecode.dex2jar.ir.ts.CleanLabel
import com.googlecode.dex2jar.ir.ts.DeadCodeTransformer
import com.googlecode.dex2jar.ir.ts.ExceptionHandlerTrim
import com.googlecode.dex2jar.ir.ts.Ir2JRegAssignTransformer
import com.googlecode.dex2jar.ir.ts.MultiArrayTransformer
import com.googlecode.dex2jar.ir.ts.NewTransformer
import com.googlecode.dex2jar.ir.ts.NpeTransformer
import com.googlecode.dex2jar.ir.ts.RemoveConstantFromSSA
import com.googlecode.dex2jar.ir.ts.RemoveLocalFromSSA
import com.googlecode.dex2jar.ir.ts.TypeTransformer
import com.googlecode.dex2jar.ir.ts.UnSSATransformer
import com.googlecode.dex2jar.ir.ts.VoidInvokeTransformer
import com.googlecode.dex2jar.ir.ts.ZeroTransformer
import com.googlecode.dex2jar.ir.ts.array.FillArrayTransformer
import com.googlecode.dex2jar.tools.Constants
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor

/**
 * Translates dex code to class files with dex2jar's `ExDex2Asm`, the way `Dex2jar.to(…)` does with the options the
 * converter used before (debug info kept, synchronized blocks optimised, no register re-use), but in memory, with names
 * left as they are, and with one fix in the IR pipeline ([InlinedConstructorCalls]).
 *
 * dex2jar sanitises names by default ('-' to '_'), in references to the host's members too, so every call to a Kotlin
 * member with a mangled name (`Duration.Companion.getZERO-UwyO8pc()`, used by every `HttpSource`'s client) failed with
 * `NoSuchMethodError`; the JVM accepts '-' in names.
 *
 * dex2jar's `NewTransformer` merges `new-instance T` and `invoke-direct S.<init>` into `new S()`, taking the type from
 * the constructor's owner. R8 inlines trivial constructors, so release extensions are full of
 * `new-instance T; invoke-direct Object.<init>` (every Kotlin `object`); ART accepts that, and dex2jar turned it into
 * `new Object()` stored where a `T` is expected, which the JVM's verifier rejects (and would be wrong without it).
 */
internal object DexTranslator {

    /** Classes written by [translate] (internal name → class file) and the constructor calls it re-targeted. */
    class Translation(val classes: Map<String, ByteArray>, val inlinedConstructors: Set<InlinedConstructor>)

    /** `new [type]` constructed by [superclass]`.<init>[descriptor]`: now `[type].<init>[markedDescriptor]`. */
    data class InlinedConstructor(val type: String, val superclass: String, val descriptor: String) {
        val markedDescriptor: String get() = markedDescriptor(descriptor, superclass)
    }

    /** [descriptor] with two marker parameters: the superclass whose constructor was inlined, then `Void`. */
    fun markedDescriptor(descriptor: String, superclass: String): String =
        descriptor.substringBeforeLast(")") + "L$superclass;$MARKER)V"

    private const val MARKER = "Ljava/lang/Void;"

    fun translate(dex: ByteArray, handler: DexExceptionHandler): Translation {
        val fileNode = DexFileNode()
        try {
            MultiDexFileReader.open(dex).accept(fileNode, DexFileReader.IGNORE_READ_EXCEPTION)
        } catch (e: Exception) {
            handler.handleFileException(e)
        }
        val classes = LinkedHashMap<String, ByteArray>()
        val factory = ClassVisitorFactory {
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            val names = LambadaNameSafeClassAdapter(writer, true)
            object : ClassVisitor(Constants.ASM_VERSION, names) {
                override fun visitEnd() {
                    super.visitEnd()
                    val bytes = try {
                        writer.toByteArray()
                    } catch (e: Exception) {
                        handler.handleFileException(e)
                        return
                    }
                    classes[names.className] = bytes
                }
            }
        }
        val inlined = InlinedConstructorCalls()
        Translator(handler, inlined).convertDex(fileNode, factory)
        return Translation(classes, inlined.found)
    }

    /** `ExDex2Asm` with the transformer chain of `Dex2jar`, plus [inlined] before `NewTransformer`. */
    private class Translator(handler: DexExceptionHandler, private val inlined: InlinedConstructorCalls) : ExDex2Asm(handler) {
        private val cleanLabel = CleanLabel()
        private val deadCode = DeadCodeTransformer()
        private val removeLocal = RemoveLocalFromSSA()
        private val removeConstant = RemoveConstantFromSSA()
        private val zero = ZeroTransformer()
        private val npe = NpeTransformer()
        private val new = NewTransformer()
        private val fillArray = FillArrayTransformer()
        private val agg = AggTransformer()
        private val multiArray = MultiArrayTransformer()
        private val voidInvoke = VoidInvokeTransformer()
        private val type = TypeTransformer()
        private val unSsa = UnSSATransformer()
        private val regAssign = Ir2JRegAssignTransformer()
        private val trimExceptions = ExceptionHandlerTrim()

        override fun convertMethod(
            classNode: DexClassNode,
            methodNode: DexMethodNode,
            cv: ClassVisitor,
            clzCtx: Dex2Asm.ClzCtx,
        ) {
            inlined.superclass = classNode.superClass
            super.convertMethod(classNode, methodNode, cv, clzCtx)
        }

        override fun optimize(irMethod: IrMethod) {
            cleanLabel.transform(irMethod)
            deadCode.transform(irMethod)
            removeLocal.transform(irMethod)
            removeConstant.transform(irMethod)
            zero.transform(irMethod)
            if (npe.transformReportChanged(irMethod)) {
                deadCode.transform(irMethod)
                removeLocal.transform(irMethod)
                removeConstant.transform(irMethod)
            }
            inlined.transform(irMethod)
            new.transform(irMethod)
            fillArray.transform(irMethod)
            agg.transform(irMethod)
            multiArray.transform(irMethod)
            voidInvoke.transform(irMethod)
            // dex2jar issue 477: dead code found in unssa, clean up
            deadCode.transform(irMethod)
            removeLocal.transform(irMethod)
            removeConstant.transform(irMethod)
            type.transform(irMethod)
            unSsa.transform(irMethod)
            regAssign.transform(irMethod)
            trimExceptions.transform(irMethod)
        }

        override fun ir2j(irMethod: IrMethod, mv: MethodVisitor, clzCtx: Dex2Asm.ClzCtx) {
            IR2JConverter()
                .optimizeSynchronized(true)
                .clzCtx(clzCtx)
                .ir(irMethod)
                .asm(mv)
                .convert()
        }
    }

    /**
     * Re-targets `x = new T; x.<init>` calls whose constructor belongs to a superclass `S` of `T` (an R8-inlined
     * constructor) to `T.<init>(…, S, Void)`, passing `null` for both markers; [InlinedConstructors] adds that
     * constructor (and its chain up to `S`) to the class files. Only receivers that are a local assigned once from
     * `new T` (directly or through copies) are re-targeted.
     *
     * The same in a constructor of `C` whose `this.<init>` call belongs to a superclass `S` beyond the direct one
     * ([superclass]): R8 inlined the constructors in between, and the JVM only accepts a call to `C`'s or its direct
     * superclass's constructor there, so it becomes `C.<init>(…, S, Void)` (MangaDex failed with "Bad <init> method
     * call").
     */
    private class InlinedConstructorCalls {
        val found: MutableSet<InlinedConstructor> = LinkedHashSet()

        /** The descriptor of the direct superclass of the class whose method is transformed next. */
        var superclass: String? = null

        fun transform(method: IrMethod) {
            val assignments = HashMap<Local, MutableList<Value>>()
            for (stmt in method.stmts) {
                val assigns = stmt.st == Stmt.ST.ASSIGN || stmt.st == Stmt.ST.IDENTITY
                if (assigns && stmt.op1.vt == Value.VT.LOCAL) {
                    assignments.getOrPut(stmt.op1 as Local) { ArrayList(1) } += stmt.op2
                }
            }
            val constructor = method.name == "<init>" && !method.isStatic && superclass != null
            if (!constructor && assignments.values.none { values -> values.any { it.vt == Value.VT.NEW } }) return

            fun newType(value: Value, depth: Int = 0): String? {
                if (value.vt != Value.VT.LOCAL || depth > MAX_COPIES) return null
                val source = assignments[value as Local]?.singleOrNull() ?: return null
                return when (source.vt) {
                    Value.VT.NEW -> (source as NewExpr).type
                    Value.VT.THIS_REF -> if (constructor) method.owner else null
                    Value.VT.LOCAL -> newType(source, depth + 1)
                    else -> null
                }
            }

            for (stmt in method.stmts) {
                if (stmt.st != Stmt.ST.VOID_INVOKE) continue
                val invoke = stmt.op as? InvokeExpr ?: continue
                if (invoke.vt != Value.VT.INVOKE_SPECIAL || invoke.name != "<init>" || invoke.ops.isEmpty()) continue
                val receiver = newType(invoke.ops[0]) ?: continue
                if (receiver == method.owner && constructor && invoke.owner == superclass) continue
                val type = internalName(receiver)
                val superclass = internalName(invoke.owner)
                if (type == superclass) continue
                val call = InlinedConstructor(type, superclass, invoke.proto.desc)
                found += call
                stmt.op = Exprs.nInvokeSpecial(
                    invoke.ops + Exprs.nNull() + Exprs.nNull(),
                    "L$type;",
                    "<init>",
                    invoke.args + "L$superclass;" + MARKER,
                    "V",
                )
            }
        }

        private fun internalName(descriptor: String) = descriptor.removePrefix("L").removeSuffix(";")

        private companion object {
            const val MAX_COPIES = 16
        }
    }
}
