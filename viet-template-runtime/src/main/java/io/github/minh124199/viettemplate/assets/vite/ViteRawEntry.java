package io.github.minh124199.viettemplate.assets.vite;

import java.util.List;

/** Internal raw representation of an entry in a Vite manifest. */
record ViteRawEntry(
    String key,
    String file,
    String src,
    String name,
    boolean isEntry,
    boolean isDynamicEntry,
    List<String> imports,
    List<String> dynamicImports,
    List<String> css,
    List<String> assets) {}
