import jade.core.AID;

public class Task {
    public AID aid;
    public int complexity = 0;

    public Task(AID id, int comp) {
        aid = id;
        complexity = comp;
    }
}
