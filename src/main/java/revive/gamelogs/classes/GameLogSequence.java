package revive.gamelogs.classes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameLogSequence {
	private List<Pair<Long, GameLogEvent>> events = new ArrayList<>();

	public void orderChronologically() {
		Collections.sort(events, (a, b) -> (int) (a.getKey() - b.getKey()));
	}

	public void addLog(GameLogEvent event) {
		events.add(new Pair<>(Instant.now().getEpochSecond(), event));
	}

	public void addLog(long time, GameLogEvent event) {
		events.add(new Pair<>(time, event));
	}
	
	public List<Pair<Long, GameLogEvent>> getEvents() {
		return events;
	}
}
