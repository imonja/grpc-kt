package io.github.imonja.grpc.kt.toolkit.template

import io.github.imonja.grpc.kt.toolkit.import.Import

data class TransformTemplateWithImports(
    val template: TransformTemplate,
    val imports: Set<Import>
) {
    companion object {
        fun of(template: TransformTemplate, imports: Set<Import> = setOf()) =
            TransformTemplateWithImports(template, imports = imports)

        fun of(template: String, imports: Set<Import> = setOf()) =
            TransformTemplateWithImports(TransformTemplate(template), imports = imports)
    }
}
