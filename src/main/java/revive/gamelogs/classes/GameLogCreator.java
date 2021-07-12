package revive.gamelogs.classes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.linkedin.migz.MiGzInputStream;
import com.linkedin.migz.MiGzOutputStream;

public class GameLogCreator {

	private Map<Class<? extends GameLogEvent>, Integer> logMap = new HashMap<>();
	private static Charset standardCharset = StandardCharsets.UTF_8;

	public void registerGameLogEventType(Class<? extends GameLogEvent> type) {
		if (verifyIntegrity(type))
			for (int i = 1; true; i++) {
				if (logMap.containsValue(i))
					continue;
				logMap.put(type, i);
				break;
			}
		else
			throw new IllegalArgumentException("Gamelog type " + type.getName() + " doesn't have empty constructor!");
	}

	private boolean verifyIntegrity(Class<? extends GameLogEvent> event) {
		try {
			event.getConstructor();
			return true;
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
	}

	private GameLogEvent buildEmptyEvent(int id)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, NullPointerException {
		return logMap.entrySet().stream().filter(c -> c.getValue() == id).findFirst().orElse(null).getKey()
				.getConstructor().newInstance();
	}

	private ByteArrayOutputStream compress(ByteArrayOutputStream str) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		MiGzOutputStream gzip = new MiGzOutputStream(compressed);
		str.writeTo(gzip);
		gzip.close();
		return compressed;
	}

	public GameLogSequence parseGameLog(byte[] buffer, boolean compression)
			throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, NullPointerException {
		InputStream inputStream = null;
		if (compression) {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
			// When reading really fast from a MiGzInputStream, for some reason it doesnt
			// always reply with decompressed data, and sometimes it gives bonkers answers.
			// Fully decompress the MiGzInputStream before working with data inside
			MiGzInputStream toDecode = new MiGzInputStream(byteStream);
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] decompressedBuffer = new byte[1024];
			for (int length; (length = toDecode.read(decompressedBuffer)) != -1;)
				result.write(decompressedBuffer, 0, length);
			toDecode.close();
			inputStream = new ByteArrayInputStream(result.toByteArray());
		} else {
			inputStream = new ByteArrayInputStream(buffer);
		}

		return parseGameLog(inputStream);
	}

	public GameLogSequence parseGameLog(InputStream inputStream)
			throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, NullPointerException {
		List<byte[]> stringPool = new ArrayList<>();
		LogStream stream = new LogStream(stringPool, inputStream, null);
		int poolSize = stream.readInt();
		for (int i = 0; i < poolSize; i++) {
			int size = stream.readInt();
			byte[] lens = new byte[size];
			stream.inputStream.read(lens, 0, size);
			stringPool.add(lens);
		}

		GameLogSequence result = new GameLogSequence();

		while (stream.getInputStream().available() != 0) {
			int id = stream.readInt();
			long timestamp = stream.readVarLong();

			GameLogEvent event = buildEmptyEvent(id);
			event.readFromStream(stream);
			result.addLog(timestamp, event);
		}

		return result;
	}

	public ByteArrayOutputStream buildGameLog(GameLogSequence sequence, boolean compression) throws IOException {
		sequence.orderChronologically();

		ByteArrayOutputStream result = new ByteArrayOutputStream();
		List<Pair<Long, GameLogEvent>> events = sequence.getEvents();
		List<byte[]> byteArrayPool = new ArrayList<>();

		for (Pair<Long, GameLogEvent> current : events) {
			ByteArrayOutputStream tempStream = new ByteArrayOutputStream();
			LogStream strm = new LogStream(byteArrayPool, null, tempStream);
			// Find gamelog id
			strm.writeInt(logMap.getOrDefault(current.getValue().getClass(), -1));
			// Write timestamp
			strm.writeVarLong(current.getKey());

			current.getValue().writeToStream(strm);

			tempStream.writeTo(result);
		}

		ByteArrayOutputStream finalStream = new ByteArrayOutputStream();
		LogStream strm = new LogStream(null, null, finalStream);
		strm.writeInt(byteArrayPool.size());
		for (int i = 0; i < byteArrayPool.size(); i++) {
			byte[] pooled = byteArrayPool.get(i);
			strm.writeInt(pooled.length);
			strm.getOutputStream().write(pooled);
		}
		result.writeTo(finalStream);

		if (compression)
			return compress(finalStream);
		return finalStream;
	}

	public static class LogStream {
		private List<byte[]> stringPool;
		private Map<Integer, Integer> hashes = new HashMap<>();

		private InputStream inputStream;
		private OutputStream outputStream;

		public LogStream(List<byte[]> stringPool, InputStream inputStream, OutputStream outputStream) {
			super();
			this.stringPool = stringPool;
			this.inputStream = inputStream;
			this.outputStream = outputStream;
		}

		public InputStream getInputStream() {
			return inputStream;
		}

		public OutputStream getOutputStream() {
			return outputStream;
		}

		public int readVarInt() throws IOException {
			int numRead = 0;
			int result = 0;
			byte read = 0;
			do {
				read = (byte) inputStream.read();
				int value = (read & 0b01111111);
				result |= (value << (7 * numRead));
				numRead++;
				if (numRead > 5) {
					throw new RuntimeException("VarInt is too big");
				}
			} while ((read & 0b10000000) != 0);

			return result;
		}

		public long readVarLong() throws IOException {
			int numRead = 0;
			long result = 0;
			byte read;
			do {
				read = (byte) inputStream.read();
				int value = (read & 0b01111111);
				result |= (value << (7 * numRead));
				numRead++;
				if (numRead > 10) {
					throw new RuntimeException("VarLong is too big");
				}
			} while ((read & 0b10000000) != 0);

			return result;
		}

		public void writeVarInt(int value) throws IOException {
			do {
				byte temp = (byte) (value & 0b01111111);
				value >>>= 7;
				if (value != 0) {
					temp |= 0b10000000;
				}
				outputStream.write(temp);
			} while (value != 0);
		}

		public void writeVarLong(long value) throws IOException {
			do {
				byte temp = (byte) (value & 0b01111111);
				value >>>= 7;
				if (value != 0) {
					temp |= 0b10000000;
				}
				outputStream.write(temp);
			} while (value != 0);
		}

		public void writeInt(int i) throws IOException {
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.putInt(i);
			outputStream.write(bb.array());
		}

		public int readInt() throws IOException {
			byte[] bfs = new byte[4];
			inputStream.read(bfs);
			ByteBuffer bfr = ByteBuffer.wrap(bfs);
			bfr.order(ByteOrder.BIG_ENDIAN);
			return bfr.getInt();
		}

		public void writeBool(boolean i) throws IOException {
			if (i)
				outputStream.write(0x01);
			else
				outputStream.write(0x00);
		}

		public boolean readBool() throws IOException {
			byte b = (byte) inputStream.read();
			if (b == 0x00)
				return false;
			return true;
		}

		public void writeShort(short i) throws IOException {
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.putShort(i);
			outputStream.write(bb.array());
		}

		public short readShort() throws IOException {
			byte[] bfs = new byte[2];
			inputStream.read(bfs);
			ByteBuffer bfr = ByteBuffer.wrap(bfs);
			bfr.order(ByteOrder.BIG_ENDIAN);
			return bfr.getShort();
		}

		@Deprecated
		public void writeString(String i) throws IOException {
			byte[] res = i.getBytes(standardCharset);
			writeInt(res.length);
			outputStream.write(res);
		}

		@Deprecated
		public String readString() throws IOException {
			int len = readInt();
			byte[] b = new byte[len];
			inputStream.read(b, 0, len);
			return new String(b, standardCharset);
		}

		public void writePooledByteArray(byte[] bytes) throws IOException {
			int pos = -1;
			int hash = Arrays.hashCode(bytes);
			if (this.hashes.containsKey(hash)) {
				pos = this.hashes.get(hash);
			} else {
				pos = this.stringPool.size();
				this.stringPool.add(bytes);
				this.hashes.put(hash, pos);
			}
			writeInt(pos);
		}

		public byte[] readPooledByteArray() throws IOException {
			int pos = readInt();
			return this.stringPool.get(pos);
		}

		public void writePooledString(String str) throws IOException {
			writePooledByteArray(str.getBytes(standardCharset));
		}

		public String readPooledString() throws IOException {
			return new String(readPooledByteArray(), standardCharset);
		}

		public void writePooledUUID(UUID u) throws IOException {
			ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
			bb.putLong(u.getMostSignificantBits());
			bb.putLong(u.getLeastSignificantBits());

			writePooledByteArray(bb.array());
		}

		public UUID readPooledUUID() throws IOException {
			ByteBuffer bb = ByteBuffer.wrap(readPooledByteArray());
			long firstLong = bb.getLong();
			long secondLong = bb.getLong();
			return new UUID(firstLong, secondLong);
		}
	}
}
