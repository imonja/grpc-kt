package io.github.imonja.grpc.kt.common

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Returns the [Metadata] from this [CoroutineContext], or null if not present.
 */
val CoroutineContext.grpcMetadata: Metadata?
    get() = GrpcMetadataContext.METADATA_KEY.get()

/**
 * Metadata utilities for gRPC.
 */
object GrpcMetadataContext {
    /**
     * Context key for gRPC [Metadata].
     */
    @JvmField
    val METADATA_KEY: Context.Key<Metadata> = Context.key("grpc-metadata")
}

/**
 * Returns a new [ServerInterceptor] that puts gRPC [Metadata] into the [Context].
 * This makes metadata available in the server implementation via `coroutineContext.grpcMetadata`.
 */
fun metadataServerInterceptor(): ServerInterceptor = object : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val context = Context.current().withValue(GrpcMetadataContext.METADATA_KEY, headers)
        return Contexts.interceptCall(context, call, headers, next)
    }
}
