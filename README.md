
# GameLogsParser
Java library for parsing and creating Game logs in the format used by RE:Vive for their games.

# Import in IDE
### You can import the parser through our Maven repository.
```xml
<repository>
	<id>revive-repository</id>
	<url>https://nexus.mcrevive.net/repository/revive-public-snapshots/</url>
</repository>
```
```xml
<dependency>
  <groupId>Revive</groupId>
  <artifactId>GameLogsParser</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

# How to use
## Parsing a game log from a file
```java
// Remember to pick the right creator for the game!
GameLogCreator gamelogCreator = CreatorBuilder.bedwarsCreator();
byte[] fileContents = Files.readAllBytes(new File("file.game").toPath());
// The second parameter refers to whether or not compression should be used.
GameLogSequence logs = gamelogCreator.parseGameLog(fileContents, true);
// You can now iterate the contents of logs.getEvents()
```

## Saving a sequence to a file
```java
// Remember to pick the right creator for the game!
GameLogCreator gamelogCreator = CreatorBuilder.bedwarsCreator();
GameLogSequence sequence = randomSequence(); // Replace this with whatever you got.
// The second parameter refers to whether or not compression should be used.
byte[] resultingLog = gamelogCreator.buildGameLog(sequence, true).toByteArray();

// Open a file stream, write the byte array of the game and close it
FileOutputStream stream = new FileOutputStream(new File("file.game"));
stream.write(resultingLog);
stream.close();
```

# Protocol for implementation
If you want to rewrite this library in some other language, you will need an accurate portrayal of the protocol.

## Basic data structures
### Integer
Integers are always 4-byte values using Big Endian notation in order to save them to a stream.
Relevant code samples **(implies existence of field named outputStream and inputStream)**

```java
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
```
### VarInt and VarLong
Check out https://wiki.vg/Protocol#VarInt_and_VarLong for more information on how this works.
Relevant code samples for implementation
```java
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
```

### Short
Shorts are 2-byte data structures based on big endian notation.
Relevant code samples
```java
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
```

### Boolean
Booleans are a single byte, either `0x00` for false or `0x01` for true
Relevant code samples
```java
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
```

### Strings
Strings are really simple data structures. They simply represent their length as an integer (see above), and then that many bytes. (standardCharset is UTF-8)
Relevant code samples
```java
public void writeString(String i) throws IOException {
	byte[] res = i.getBytes(standardCharset);
	writeInt(res.length);
	outputStream.write(res);
}

public String readString() throws IOException {
	int len = readInt();
	byte[] b = new byte[len];
	inputStream.read(b, 0, len);
	return new String(b, standardCharset);
}
```

## The byte array pool
### Usage
In order to benefit from repeating data structures (such as UUID's or usernames) we start each game log sequence with a pool of byte arrays which can be reused across individual game log events. Each game log sequence has **one** byte array pool.
### Structure
The general structure of a byte array is it's length (as an integer), followed by that many bytes. Likewise, a pool of byte arrays is it's length (as an integer) followed by that many byte arrays. 
### Building the pool
When converting gamelogs, their appropriate order of reading should be used, sorted chronologically based on their timestamps. Pools start from 0, and can be increased to an arbitrary size.
### Example pool
```
[0,0,0,2,0,0,0,3,100,200,300,0,0,0,6,100,100,100,100,100,100] 
```
Looking at this pool, we can first identify the size of it, by looking at the first four bytes
```java
[| 0,0,0,2 | ,0,0,0,3,100,200,300,0,0,0,6,100,100,100,100,100,100] 
```
It's a size 2 pool. It is then followed by two byte arrays of lengths 3 and 6
```java
[| 0,0,0,2 | ,||0,0,0,3 * 100,200,300||,||0,0,0,6 * 100,100,100,100,100,100||] 
     ^            ^          ^               ^                 ^
  pool size      s1.len    s1.data          s2.len           s2.data
```
In JSON, the parsed values would look as such (obviously characters will need to be mutated into their form conforming to the charset)
```json
[
	[100,200,300]
	[100,100,100,100,100,100]
]
```
A single integer is then used inside events in order to referrence the pool.

### Pooling arbitrary data algorithm implementation
In order to allow for bidirectional approach for `index <-> array` pairs, we have concluded that using two separate data structures is appropriate. We have used an ArrayList containing the pooled byte arrays, which allows for easy index based accessing. We then used a HashMap which has array hashes (calculated using `Arrays#hashCode(byte[])`) as the key, and an Integer, which represents the index of the array inside the aforementioned list.  This leads to a very simple implementation, low memory footprint, and overall linear times in both parsing and serializing game data.
Relevant code samples.
```java
private List<byte[]> stringPool = new ArrayList<>();
private Map<Integer, Integer> hashes = new HashMap<>();

public void writePooledByteArray(byte[] bytes) throws IOException {
	int pos = -1;
	int hash = Arrays.hashCode(bytes);
	// Check if the map contains hash
	if (this.hashes.containsKey(hash)) {
		pos = this.hashes.get(hash); // Fetch the index of the byte array
	} else {
	    // This is a new string, add to pool, position is previous size
		pos = this.stringPool.size();
		this.stringPool.add(bytes);
		this.hashes.put(hash, pos);
	}
	// Write an integer represeting the index in the pool.
	writeInt(pos);
}

public byte[] readPooledByteArray() throws IOException {
	// Read the referrence to the pool
	int pos = readInt();
	// Return the byte array at that position
	return this.stringPool.get(pos);
}
```

### Pooling UUID's
In order to optimise the space used by UUID's, which are a prominent form of data storage in the medium targeted, we have opted to store them as raw bytes as opposed to their string representation, achieving a ~55% memory footprint reduction whenever one is pooled. 
**It should be noted that the `UUID#nameUUIDFromBytes(byte[])` will not produce the intended results. Check with your own language's documentation.**
Relevant code samples for pooling UUID's
```java
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
```
Here is an example for testing in other languages
```
UUID String form: 652b02df-ff0f-4e93-a4b2-24381eaa399a
Byte Array representation:
[101, 43, 2, -33, -1, 15, 78, -109, -92, -78, 36, 56, 30, -86, 57, -102]

UUID Internal data:
MostSignificantBits: 7289923582909435539
LeastSignificantBits: -6579156282183632486
```

## Gamelog event structure
Each gamelog event has three elements to it, written and read in the following order.

|Data name  | Data type | Notes |
|--|--|--|
| ID | Integer | Gamemode dependant |
| Timestamp |Long | UNIX timestamp, seconds
|Event data| N/A| Depends on the ID.


## Entire gamelog structure
The gamelog is split intro two pieces. The first piece is the byte array pool, and is then followed by an arbitrary unknown amount of gamelogs. The amount is not specified anywhere and should be assumed to be constant until the stream or data ends.

## Gamemode Logs ID's
### Bedwars
#### Chat Game Log (ID: 1)
| Data name | Data type |
|--|--|
| Username | Pooled String |
| Message | Pooled String |
#### Bed Break Log (ID: 2)
| Data name | Data type |
|--|--|
| Bed Destroyer | Pooled String |
| Destroyed Team | Pooled String |

#### Kill Log (ID: 3)
| Data name | Data type |
|--|--|
| Killer username | Pooled String |
| Killed username | Pooled String |
| Weapon | Pooled String |
#### Death Log (ID: 4)
| Data name | Data type |
|--|--|
| Player death | Pooled String |
| Type (cause) | Pooled String |


## Final example of Protocol Parsing
Suppose we have the following raw data. This is a bedwars game.
```json
[0, 0, 0, 7, 0, 0, 0, 16, 83, 111, 109, 101, 82, 97, 110, 100, 111, 109, 80, 108, 97, 121, 101, 114, 0, 0, 0, 3, 82, 101, 100, 0, 0, 0, 8, 74, 118, 115, 116, 77, 97, 116, 116, 0, 0, 0, 15, 72, 101, 108, 108, 111, 32, 101, 118, 101, 114, 121, 111, 110, 101, 33, 0, 0, 0, 4, 70, 65, 76, 76, 0, 0, 0, 7, 71, 97, 114, 103, 97, 110, 116, 0, 0, 0, 13, 68, 73, 65, 77, 79, 78, 68, 95, 83, 87, 79, 82, 68, 0, 0, 0, 2, -60, -110, -74, -121, 6, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0, 3, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 5, 0, 0, 0, 6]
```
We will start by analysing the byte array pool. We can see there are 7 objects in this byte array pool, and are as follows. (Remember the first four bytes are the length of the String.
```json
[
[0, 0, 0, 16, 83, 111, 109, 101, 82, 97, 110, 100, 111, 109, 80, 108, 97, 121, 101, 114],
[0, 0, 0, 3, 82, 101, 100],
[0, 0, 0, 8, 74, 118, 115, 116, 77, 97, 116, 116],
[0, 0, 0, 15, 72, 101, 108, 108, 111, 32, 101, 118, 101, 114, 121, 111, 110, 101, 33],
[0, 0, 0, 4, 70, 65, 76, 76],
[0, 0, 0, 7, 71, 97, 114, 103, 97, 110, 116],
[0, 0, 0, 13, 68, 73, 65, 77, 79, 78, 68, 95, 83, 87, 79, 82, 68],
]
```

When interpreted, this byte array pool looks as such
```json
[
"SomeRandomPlayer",
"Red",
"Jvst Matt",
"Hello everyone!",
"FALL",
"Gargant",
"DIAMOND_SWORD"
]
```

With the byte array pool interpreted we only have the events part left.
```json
[0, 0, 0, 2, -60, -110, -74, -121, 6, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0, 3, -60, -110, -74, -121, 6, 0, 0, 0, 2, 0, 0, 0, 5, 0, 0, 0, 6]
 ```
We start by looking at the ID.
It's a 2, which means it's a Bed Break Log, and then we also fetch the timestamp as a VarLong.
ID: `[0, 0, 0, 2]`
Timestamp: `[-60, -110, -74, -121, 6]`
Since it is a bed break log, it should then be followed by two more integers which are the referrences in the pool to the strings. In this case, the indexes are `[0,  0,  0,  0]` and a `[0,  0,  0,  1]` which refer to 
`"SomeRandomPlayer"` and `"Red"`. 

We are then left with 
```json
[0,  0,  0,  1,  -60,  -110,  -74,  -121,  6,  0,  0,  0,  2,  0,  0,  0,  3,  0,  0,  0,  4,  -60,  -110,  -74,  -121,  6,  0,  0,  0,  2,  0,  0,  0,  4,  0,  0,  0,  3,  -60,  -110,  -74,  -121,  6,  0,  0,  0,  2,  0,  0,  0,  5,  0,  0,  0,  6]
```

Which, after fully being parsed should turn into three more logs, in this order
- ChatGameLog ("JvstMatt", "Hello everyone!")
- DeathLog ("JvstMatt", "FALL")
- KillLog("JvstMatt", "Gargant", "DIAMOND_SWORD")

And with that, we have fully decoded a gamelog into usable data.

## Compression
If some data seems strange, and you cannot make sense of it, especially if downloaded from the Revive, it is probably because it's compressed. The compression is pretty simple to undo, as it's just simple GZIP compression. 

## Thank you
I'd like to thank everyone on the Revive staff team, and to all the players for making all of this possible!