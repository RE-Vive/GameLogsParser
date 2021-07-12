package revive.gamelogs.logs;

import revive.gamelogs.classes.GameLogCreator;
import revive.gamelogs.logs.bedwars.BedBreakLog;
import revive.gamelogs.logs.bedwars.ChatGameLog;
import revive.gamelogs.logs.bedwars.DeathLog;
import revive.gamelogs.logs.bedwars.KillLog;

public class CreatorBuilder {
	public static GameLogCreator bedwarsCreator() {
		GameLogCreator creator = new GameLogCreator();
		// Register id's are incremental
		creator.registerGameLogEventType(ChatGameLog.class);
		creator.registerGameLogEventType(BedBreakLog.class);
		creator.registerGameLogEventType(KillLog.class);
		creator.registerGameLogEventType(DeathLog.class);
		return creator;
	}
}
