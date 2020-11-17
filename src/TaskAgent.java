import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;

public class TaskAgent extends Agent {

    Task task;

    @Override
    protected void setup() {
        System.out.println("Task " + getLocalName()+" is ready");

    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
