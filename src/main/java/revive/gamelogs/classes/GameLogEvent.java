package revive.gamelogs.classes;

import revive.gamelogs.classes.GameLogCreator.LogStream;

public abstract class GameLogEvent {
	public abstract void writeToStream(LogStream stream);

	public abstract void readFromStream(LogStream stream);
}
