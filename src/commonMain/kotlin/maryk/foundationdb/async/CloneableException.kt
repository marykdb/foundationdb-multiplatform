package maryk.foundationdb.async

expect interface CloneableException {
    fun retargetClone(): Exception
}
