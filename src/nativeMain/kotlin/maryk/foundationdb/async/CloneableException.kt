package maryk.foundationdb.async

actual interface CloneableException {
    actual fun retargetClone(): Exception
}
