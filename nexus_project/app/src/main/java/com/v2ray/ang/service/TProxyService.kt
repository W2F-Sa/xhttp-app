package com.v2ray.ang.service

/**
 * JNI bridge to the prebuilt libhev-socks5-tunnel.so native library.
 *
 * IMPORTANT: This class MUST live in the package `com.v2ray.ang.service` with the exact
 * class name `TProxyService` and the exact native method names below. The native library
 * was compiled with -DPKGNAME=com/v2ray/ang/service and registers its JNI functions
 * dynamically against this fully-qualified class name. Renaming or relocating this class
 * will break the JNI binding at runtime.
 *
 * The native library (hev-socks5-tunnel by heiher, MIT licensed) bridges packets from the
 * Android VPN TUN file descriptor to a local SOCKS5 proxy (provided by the Xray core).
 */
object TProxyService {

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName")
    external fun TProxyGetStats(): LongArray?
}
