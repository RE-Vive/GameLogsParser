package revive.gamelogs.logs.bedwars;

import java.io.IOException;

import revive.gamelogs.classes.GameLogCreator.LogStream;
import revive.gamelogs.classes.GameLogEvent;

public class BedBreakLog extends GameLogEvent {

	private String bedDestroyer;
	private String destroyedTeam;

	public BedBreakLog(String bedDestroyer, String destroyedTeam) {
		this.bedDestroyer = bedDestroyer;
		this.destroyedTeam = destroyedTeam;
	}

	public BedBreakLog() {

	}

	@Override
	public void writeToStream(LogStream stream) {
		try {
			stream.writePooledString(bedDestroyer);
			stream.writePooledString(destroyedTeam);
		} catch (Exception ex) {

		}
	}

	@Override
	public void readFromStream(LogStream stream) {
		try {
			bedDestroyer = stream.readPooledString();
			destroyedTeam = stream.readPooledString();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
