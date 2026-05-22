/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * A thread-safe circular byte queue that replaces standard PipedInputStream/PipedOutputStream.
 * This class is entirely thread-agnostic and is immune to thread pool worker thread terminations.
 */
class VirtualStream : InputStream() {
    private val queue = LinkedBlockingQueue<Int>()
    @Volatile private var closed = false

    val outputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (closed) throw IOException("Stream closed")
            queue.put(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("Stream closed")
            for (i in 0 until len) {
                queue.put(b[off + i].toInt() and 0xFF)
            }
        }

        override fun close() {
            closed = true
            queue.put(-1) // EOF poison pill
        }
    }

    override fun read(): Int {
        if (closed && queue.isEmpty()) return -1
        val b = queue.take()
        if (b == -1) {
            closed = true
            return -1
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val first = read()
        if (first == -1) return -1
        b[off] = first.toByte()
        var bytesRead = 1
        while (bytesRead < len) {
            val next = queue.poll() ?: break
            if (next == -1) {
                closed = true
                queue.put(-1)
                break
            }
            b[off + bytesRead] = next.toByte()
            bytesRead++
        }
        return bytesRead
    }

    override fun close() {
        closed = true
        queue.put(-1)
    }
}
