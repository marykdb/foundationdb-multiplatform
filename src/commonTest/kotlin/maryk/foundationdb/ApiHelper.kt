package maryk.foundationdb

internal fun ensureApiVersionSelected() {
    if (!FDB.isAPIVersionSelected()) {
        FDB.selectAPIVersion(ApiVersion.LATEST)
    }
}
