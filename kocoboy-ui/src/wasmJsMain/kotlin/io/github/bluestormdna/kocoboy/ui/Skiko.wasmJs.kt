@file:JsModule("./skiko.mjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.bluestormdna.kocoboy.ui

// Exported by skiko.mjs, its _ field holds skiko's wasm exports once loaded
internal external val loadedWasm: JsAny
