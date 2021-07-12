package revive.gamelogs.logs.bedwars;

import java.io.IOException;

import revive.gamelogs.classes.GameLogCreator.LogStream;
import revive.gamelogs.classes.GameLogEvent;

public class KillLog extends GameLogEvent {
	private String killer;
	private String killed;
	private String weapon;

	public KillLog(String killer, String killed, String weapon) {
		this.killer = killer;
		this.killed = killed;
		this.weapon = weapon;
	}

	public KillLog() {
	}

	@Override
	public void writeToStream(LogStream stream) {
		try {
			stream.writePooledString(killer);
			stream.writePooledString(killed);
			stream.writePooledString(weapon);
		} catch (Exception ex) {

		}
	}

	@Override
	public void readFromStream(LogStream stream) {
		try {
			killer = stream.readPooledString();
			killed = stream.readPooledString();
			weapon = stream.readPooledString();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
