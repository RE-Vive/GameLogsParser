package revive.gamelogs.logs.bedwars;

import java.io.IOException;

import revive.gamelogs.classes.GameLogCreator.LogStream;
import revive.gamelogs.classes.GameLogEvent;

public class ChatGameLog extends GameLogEvent {

	private String username;
	private String message;

	public ChatGameLog(String username, String message) {
		super();
		this.username = username;
		this.message = message;
	}

	public ChatGameLog() {
		super();
	}

	public String getMessage() {
		return message;
	}

	public String getUsername() {
		return username;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public void writeToStream(LogStream stream) {
		try {
			stream.writePooledString(username);
			stream.writePooledString(message);
		} catch (IOException e) {
		}
	}

	@Override
	public void readFromStream(LogStream stream) {
		try {
			username = stream.readPooledString();
			message = stream.readPooledString();
		} catch (IOException e) {
		}
	}

}
