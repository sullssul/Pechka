import jade.core.AID;

public class Task {
    public AID aid;
    public int complexity = 0;
    public String name;

    public Task(AID id, int comp, String _name) {
        aid = id;
        complexity = comp;
        name = _name;
    }
}
