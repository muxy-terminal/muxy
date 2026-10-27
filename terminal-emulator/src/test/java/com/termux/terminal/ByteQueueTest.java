package com.termux.terminal;

import junit.framework.TestCase;

public class ByteQueueTest extends TestCase {

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		if (expected.length != actual.length) {
			fail("Difference array length");
		}
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] != actual[i]) {
				fail("Inequals at index=" + i + ", expected=" + (int) expected[i] + ", actual=" + (int) actual[i]);
			}
		}
	}

	public void testCompleteWrites() throws Exception {
		ByteQueue q = new ByteQueue(10);
		assertTrue(q.write(new byte[]{1, 2, 3}, 0, 3));

		byte[] arr = new byte[10];
		assertEquals(3, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3}, new byte[]{arr[0], arr[1], arr[2]});

		assertTrue(q.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10));
		assertEquals(10, q.read(arr, true));
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, arr);
	}

	public void testQueueWraparound() throws Exception {
		ByteQueue q = new ByteQueue(10);

		byte[] origArray = new byte[]{1, 2, 3, 4, 5, 6};
		byte[] readArray = new byte[origArray.length];
		for (int i = 0; i < 20; i++) {
			q.write(origArray, 0, origArray.length);
			assertEquals(origArray.length, q.read(readArray, true));
			assertArrayEquals(origArray, readArray);
		}
	}

	public void testWriteNotesClosing() throws Exception {
		ByteQueue q = new ByteQueue(10);
		q.close();
		assertFalse(q.write(new byte[]{1, 2, 3}, 0, 3));
	}

	public void testReadNonBlocking() throws Exception {
		ByteQueue q = new ByteQueue(10);
		assertEquals(0, q.read(new byte[128], false));
	}

	public void testClosePreservesAcceptedOutputUntilDrained() {
		ByteQueue queue = new ByteQueue(4);
		assertTrue(queue.write(new byte[]{1, 2, 3, 4}, 0, 4));
		byte[] prefix = new byte[3];
		assertEquals(3, queue.read(prefix, false));
		assertTrue(queue.write(new byte[]{5, 6, 7}, 0, 3));
		queue.close();
		assertFalse(queue.write(new byte[]{8}, 0, 1));
		byte[] remaining = new byte[4];
		assertEquals(4, queue.read(remaining, false));
		assertArrayEquals(new byte[]{4, 5, 6, 7}, remaining);
		assertEquals(-1, queue.read(remaining, true));
	}

	public void testOutputResumesAfterAnEmptyQueueAndBlockedProducer() throws Exception {
		ByteQueue queue = new ByteQueue(4);
		byte[] expected = new byte[4096];
		for (int i = 0; i < expected.length; i++) expected[i] = (byte) i;
		Thread producer = new Thread(() -> {
			queue.write(expected, 0, expected.length);
			queue.close();
		});
		producer.setDaemon(true);
		assertEquals(0, queue.read(new byte[4], false));
		producer.start();
		byte[] actual = new byte[expected.length];
		byte[] chunk = new byte[3];
		int offset = 0;
		try {
			while (offset < actual.length) {
				int read = queue.read(chunk, true);
				assertTrue(read > 0);
				System.arraycopy(chunk, 0, actual, offset, read);
				offset += read;
			}
			assertArrayEquals(expected, actual);
		} finally {
			queue.close();
			producer.join(1000);
		}
		assertFalse(producer.isAlive());
		assertEquals(-1, queue.read(chunk, false));
	}

	public void testCloseWakesAProducerWaitingForSpace() throws Exception {
		ByteQueue queue = new ByteQueue(1);
		assertTrue(queue.write(new byte[]{1}, 0, 1));
		java.util.concurrent.atomic.AtomicBoolean accepted = new java.util.concurrent.atomic.AtomicBoolean(true);
		Thread producer = new Thread(() -> accepted.set(queue.write(new byte[]{2}, 0, 1)));
		producer.setDaemon(true);
		producer.start();
		queue.close();
		producer.join(1000);
		assertFalse(producer.isAlive());
		assertFalse(accepted.get());
		byte[] remaining = new byte[1];
		assertEquals(1, queue.read(remaining, false));
		assertEquals(1, remaining[0]);
		assertEquals(-1, queue.read(remaining, false));
	}

	public void testInvalidCapacityAndRangesAreRejected() {
		org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> new ByteQueue(0));
		ByteQueue queue = new ByteQueue(4);
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> queue.write(new byte[1], -1, 1));
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> queue.write(new byte[1], Integer.MAX_VALUE, 1));
		org.junit.Assert.assertThrows(IllegalArgumentException.class,
			() -> queue.read(new byte[0], false));
	}

}
