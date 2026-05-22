/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Detailed unit tests for the VirtualStream circular queue byte stream.
 * Verifies concurrency, block reads/writes, EOF handling, and edge conditions.
 */
class VirtualStreamTest {

    @Test
    fun testWriteAndReadSingleBytes() {
        val stream = VirtualStream()
        val out = stream.outputStream

        out.write(10)
        out.write(20)
        out.write(30)

        assertEquals(10, stream.read())
        assertEquals(20, stream.read())
        assertEquals(30, stream.read())
    }

    @Test
    fun testWriteAndReadChunks() {
        val stream = VirtualStream()
        val out = stream.outputStream

        val data = byteArrayOf(1, 2, 3, 4, 5)
        out.write(data)

        val buffer = ByteArray(5)
        val readBytes = stream.read(buffer)

        assertEquals(5, readBytes)
        assertArrayEquals(data, buffer)
    }

    @Test
    fun testPartialReads() {
        val stream = VirtualStream()
        val out = stream.outputStream

        out.write(byteArrayOf(1, 2, 3))
        out.write(byteArrayOf(4, 5, 6, 7))

        val buffer = ByteArray(5)
        // Should read first chunk (size 3)
        val firstRead = stream.read(buffer, 0, 5)
        assertEquals(3, firstRead)
        assertEquals(1, buffer[0].toInt())
        assertEquals(2, buffer[1].toInt())
        assertEquals(3, buffer[2].toInt())

        // Next read should fetch from second chunk
        val secondRead = stream.read(buffer, 0, 5)
        assertEquals(4, secondRead)
        assertEquals(4, buffer[0].toInt())
        assertEquals(5, buffer[1].toInt())
        assertEquals(6, buffer[2].toInt())
        assertEquals(7, buffer[3].toInt())
    }

    @Test
    fun testEOFPoisonPill() {
        val stream = VirtualStream()
        val out = stream.outputStream

        out.write(10)
        out.close()

        assertEquals(10, stream.read())
        assertEquals(-1, stream.read())
        assertEquals(-1, stream.read(ByteArray(5)))
    }

    @Test
    fun testReadAfterClose() {
        val stream = VirtualStream()
        val out = stream.outputStream

        out.write(byteArrayOf(1, 2, 3))
        out.close()

        val buffer = ByteArray(5)
        val readBytes = stream.read(buffer)
        assertEquals(3, readBytes)
        assertEquals(1, buffer[0].toInt())

        assertEquals(-1, stream.read())
    }

    @Test
    fun testBlockingRead() {
        val stream = VirtualStream()
        val out = stream.outputStream
        val latch = CountDownLatch(1)
        val result = AtomicInteger(-1)

        Thread {
            result.set(stream.read())
            latch.countDown()
        }.start()

        // Sleep to ensure the read thread blocks
        Thread.sleep(200)
        assertEquals(-1, result.get()) // Still blocked

        out.write(42)

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(42, result.get())
    }

    @Test
    fun testMultiThreadedWrites() {
        val stream = VirtualStream()
        val out = stream.outputStream
        val numThreads = 5
        val writesPerThread = 100
        val latch = CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            Thread {
                for (i in 0 until writesPerThread) {
                    out.write(t)
                }
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        out.close()

        val expectedTotal = numThreads * writesPerThread
        var actualCount = 0
        val counts = IntArray(numThreads)

        while (true) {
            val b = stream.read()
            if (b == -1) break
            counts[b]++
            actualCount++
        }

        assertEquals(expectedTotal, actualCount)
        for (t in 0 until numThreads) {
            assertEquals(writesPerThread, counts[t])
        }
    }

    @Test
    fun testWriteToClosedStreamThrows() {
        val stream = VirtualStream()
        val out = stream.outputStream
        out.close()

        try {
            out.write(10)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Stream closed", e.message)
        }
    }
}
