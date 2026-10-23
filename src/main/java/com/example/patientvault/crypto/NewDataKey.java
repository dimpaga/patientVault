package com.example.patientvault.crypto;

/** A freshly generated data key together with its wrapped form for storage. */
public record NewDataKey(DataKey key, WrappedDataKey wrapped) {
}
