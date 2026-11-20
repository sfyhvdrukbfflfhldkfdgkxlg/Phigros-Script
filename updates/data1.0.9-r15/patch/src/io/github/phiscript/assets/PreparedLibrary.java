package io.github.phiscript.assets;

import io.github.phiscript.engine.Chart;

/** Implemented by the two native library classes alongside their explicit source loaders. */
public interface PreparedLibrary {
    Chart openPrepared(String id, String level) throws Exception;
}
