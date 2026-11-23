package com.alekseevich47.notes.data.download

enum class ModelKind(val folder: String, val title: String, val detail: String) {
    T_ONE(
        folder = "t-one",
        title = "T-one",
        detail = "Потоковое распознавание на устройстве",
    ),
    GIGA_AM(
        folder = "gigaam",
        title = "GigaAM v3",
        detail = "Финальная обработка текста.",
    ),
}

data class ModelFile(
    val name: String,
    val url: String,
)

object ModelCatalog {
    private const val TONE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08/resolve/main/"
    private const val GIGA =
        "https://huggingface.co/Alexxerm/gigaam-v3-e2e-rnnt-sherpa-onnx/resolve/main/"

    fun files(kind: ModelKind): List<ModelFile> = when (kind) {
        ModelKind.T_ONE -> listOf(
            ModelFile("model.onnx", TONE + "model.onnx"),
            ModelFile("tokens.txt", TONE + "tokens.txt"),
        )
        ModelKind.GIGA_AM -> listOf(
            ModelFile("encoder.int8.onnx", GIGA + "encoder.int8.onnx"),
            ModelFile("decoder.onnx", GIGA + "decoder.onnx"),
            ModelFile("joiner.onnx", GIGA + "joiner.onnx"),
            ModelFile("tokens.txt", GIGA + "tokens.txt"),
        )
    }
}
