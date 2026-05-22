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
 * A high-performance thread-safe circular byte queue that replaces PipedInputStream/PipedOutputStream.
 * Uses chunks of ByteArrays to prevent GC pressure and boxing overhead when handling large payloads.
 * This class is entirely thread-agnostic and is immune to thread pool worker thread terminations.
 */
class VirtualStream : InputStream() {
    companion object {
        // Reference-identical poison pill to cleanly signal EOF
        private val POISON_PILL = ByteArray(0)
    }

    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false

    // Buffer tracking state for the current chunk being read
    private var currentBuffer: ByteArray? = null
    private var currentPos = 0

    val outputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (closed) throw IOException("Stream closed")
            queue.put(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("Stream closed")
            if (len > 0) {
                queue.put(b.copyOfRange(off, off + len))
            }
        }

        override fun close() {
            closed = true
            queue.put(POISON_PILL)
        }
    }

    override fun read(): Int {
        if (closed && currentBuffer == null) return -1
        val buffer = getOrFetchBuffer() ?: return -1
        val b = buffer[currentPos++].toInt() and 0xFF
        if (currentPos >= buffer.size) {
            currentBuffer = null
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (closed && currentBuffer == null && queue.isEmpty()) return -1

        val buffer = getOrFetchBuffer() ?: return -1
        val available = buffer.size - currentPos
        val bytesToCopy = Math.min(len, available)
        System.arraycopy(buffer, currentPos, b, off, bytesToCopy)
        currentPos += bytesToCopy
        if (currentPos >= buffer.size) {
            currentBuffer = null
        }
        return bytesToCopy
    }

    private fun getOrFetchBuffer(): ByteArray? {
        var buffer = currentBuffer
        if (buffer == null) {
            val next = queue.take()
            if (next === POISON_PILL || next.isEmpty()) {
                closed = true
                return null
            }
            buffer = next
            currentBuffer = next
            currentPos = 0
        }
        return buffer
    }

    override fun close() {
        closed = true
        queue.put(POISON_PILL)
    }
}
