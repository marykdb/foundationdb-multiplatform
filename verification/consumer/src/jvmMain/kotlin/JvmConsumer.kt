import com.apple.foundationdb.Database as JavaDatabase
import maryk.foundationdb.Database
import maryk.foundationdb.FDBException
import maryk.foundationdb.jvm.asJavaDatabase
import maryk.foundationdb.tuple.TupleInteger
import maryk.foundationdb.tuple.toJavaBigInteger

fun javaDatabase(database: Database): JavaDatabase = database.asJavaDatabase()
fun errorCode(error: FDBException): Int = error.code
fun integer() = TupleInteger.parse("18446744073709551616").toJavaBigInteger()
