@file:JsModule("./skiko.mjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.bluestormdna.kocoboy.ui

// Skiko's wasm exports, filled in once it has loaded
internal external val loadedWasm: JsAny
