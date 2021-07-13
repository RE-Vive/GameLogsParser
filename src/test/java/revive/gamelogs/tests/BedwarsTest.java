package revive.gamelogs.tests;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import revive.gamelogs.classes.GameLogCreator;
import revive.gamelogs.classes.GameLogEvent;
import revive.gamelogs.classes.GameLogSequence;
import revive.gamelogs.logs.CreatorBuilder;
import revive.gamelogs.logs.bedwars.BedBreakLog;
import revive.gamelogs.logs.bedwars.ChatGameLog;
import revive.gamelogs.logs.bedwars.DeathLog;
import revive.gamelogs.logs.bedwars.KillLog;

class BedwarsTest {

	private GameLogCreator bedwarsCreator;

	private GameLogSequence randomSequence() {
		GameLogSequence sequence = new GameLogSequence();
		sequence.addLog(new BedBreakLog("SomeRandomPlayer", "Red"));
		sequence.addLog(new ChatGameLog("JvstMatt", "Hello everyone!"));
		sequence.addLog(new DeathLog("JvstMatt", "FALL"));
		sequence.addLog(new KillLog("JvstMatt", "Gargant", "DIAMOND_SWORD"));
		return sequence;
	}

	@BeforeEach
	void setUp() throws Exception {
		this.bedwarsCreator = CreatorBuilder.bedwarsCreator();
	}

	@Test
	void testSerialization() throws Exception {
		GameLogSequence sequence = randomSequence();

		byte[] resultingLog = this.bedwarsCreator.buildGameLog(sequence, false).toByteArray();
		System.out.println(Arrays.toString(resultingLog));

		GameLogSequence parsed = this.bedwarsCreator.parseGameLog(resultingLog, false);

		for (int i = 0; i < sequence.getEvents().size(); i++) {
			GameLogEvent knownEvent = sequence.getEvents().get(i).getValue();
			GameLogEvent supposedEvent = parsed.getEvents().get(i).getValue();
			if (!knownEvent.getClass().equals(supposedEvent.getClass()))
				fail("Non matching classtypes!");
		}

		assertEquals(sequence.getEvents().size(), parsed.getEvents().size());
	}

	@Test
	void testCompression() throws Exception {
		GameLogSequence sequence = randomSequence();
		byte[] resultingLog = this.bedwarsCreator.buildGameLog(sequence, true).toByteArray();

		// Test for usability
		this.bedwarsCreator.parseGameLog(resultingLog, true);

		// It should never take more than 1kb to store 4 logs
		assertEquals(true, resultingLog.length < 1000);
	}

	@Test
	public void checkFileReading()
			throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, NullPointerException {
		byte[] strmc = Files.readAllBytes(new File("src/test/resources/bwgame_compressed.game").toPath());
		byte[] strmu = Files.readAllBytes(new File("src/test/resources/bwgame.game").toPath());

		// That file contains the same random sequence.
		GameLogSequence seq = this.bedwarsCreator.parseGameLog(strmc, true);
		GameLogSequence sequ = this.bedwarsCreator.parseGameLog(strmu, false);
		// They are the same game, so it should always be 0
		assertEquals(0, seq.getEvents().size() - sequ.getEvents().size());
	}
}
