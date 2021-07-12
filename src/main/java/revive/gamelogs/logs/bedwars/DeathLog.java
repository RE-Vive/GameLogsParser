package revive.gamelogs.logs.bedwars;

import java.io.IOException;

import revive.gamelogs.classes.GameLogCreator.LogStream;
import revive.gamelogs.classes.GameLogEvent;

public class DeathLog extends GameLogEvent {

	private String playerDied;
	private String type;

	public DeathLog(String playerDied, String type) {
		this.playerDied = playerDied;
		this.type = type;
	}

	public DeathLog() {

	}

	@Override
	public void writeToStream(LogStream stream) {
		try {
			stream.writePooledString(playerDied);
			stream.writePooledString(type);
		} catch (IOException e) {
		}
	}

	@Override
	public void readFromStream(LogStream stream) {
		try {
			playerDied = stream.readPooledString();
			type = stream.readPooledString();
		} catch (IOException e) {
		}
	}

}
