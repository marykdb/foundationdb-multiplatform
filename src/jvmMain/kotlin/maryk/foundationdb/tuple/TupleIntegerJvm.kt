package maryk.foundationdb.tuple

import java.math.BigInteger

fun TupleInteger.toJavaBigInteger(): BigInteger = BigInteger(sign, magnitudeBytes())
fun BigInteger.toTupleInteger(): TupleInteger = TupleInteger.fromMagnitude(signum(), abs().toByteArray())
